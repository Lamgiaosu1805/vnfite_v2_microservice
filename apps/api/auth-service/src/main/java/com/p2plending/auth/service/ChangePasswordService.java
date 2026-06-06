package com.p2plending.auth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2plending.auth.config.RedisNamespaceProperties;
import com.p2plending.auth.domain.entity.User;
import com.p2plending.auth.domain.model.PendingPasswordChange;
import com.p2plending.auth.domain.repository.UserRepository;
import com.p2plending.auth.dto.request.ChangePasswordInitRequest;
import com.p2plending.auth.dto.request.ChangePasswordVerifyRequest;
import com.p2plending.auth.exception.InvalidCredentialsException;
import com.p2plending.auth.exception.InvalidOtpException;
import com.p2plending.auth.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChangePasswordService {

    private static final String PENDING_PREFIX       = "pwd_change:";
    private static final String REFRESH_TOKEN_PREFIX = "refresh_token:";
    private static final Duration PENDING_TTL        = Duration.ofMinutes(5);
    private static final String MOCK_OTP             = "000000";

    @Value("${app.otp.mock:false}")
    private boolean mockMode;

    private final UserRepository      userRepository;
    private final PasswordEncoder     passwordEncoder;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper        objectMapper;
    private final OtpRateLimitService otpRateLimitService;
    private final RedisNamespaceProperties redisNamespaceProperties;

    // ── Bước 1: xác minh mật khẩu hiện tại → gửi OTP ────────────────────

    @Transactional(readOnly = true)
    public Map<String, String> initChange(String userId, String phone, ChangePasswordInitRequest request) {
        otpRateLimitService.assertCanRequest(phone);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new InvalidCredentialsException("Mật khẩu hiện tại không đúng");
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new InvalidCredentialsException("Mật khẩu mới phải khác mật khẩu hiện tại");
        }

        String newPasswordHash = passwordEncoder.encode(request.getNewPassword());
        String otp = mockMode ? MOCK_OTP
                : String.format("%06d", new SecureRandom().nextInt(1_000_000));

        PendingPasswordChange pending = new PendingPasswordChange(newPasswordHash, otp);
        try {
            redisTemplate.opsForValue().set(
                    pendingKey(userId),
                    objectMapper.writeValueAsString(pending),
                    PENDING_TTL
            );
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize pending password change", e);
        }

        if (mockMode) {
            log.info("[MOCK] Change password OTP for userId={}: {}", userId, otp);
        } else {
            // TODO: gửi OTP qua SMS / Zalo ZNS
            log.info("Change password OTP sent for userId={}", userId);
        }

        Map<String, String> response = new HashMap<>();
        response.put("message", "OTP đã được gửi đến số điện thoại của bạn. Vui lòng nhập OTP để xác nhận đổi mật khẩu.");
        if (mockMode) response.put("otp", otp);
        return response;
    }

    // ── Bước 2: xác thực OTP → đặt mật khẩu mới ─────────────────────────

    @Transactional
    public void verifyChange(String userId, String phone, ChangePasswordVerifyRequest request) {
        String json = redisTemplate.opsForValue().get(pendingKey(userId));
        if (json == null) {
            throw new InvalidOtpException("OTP đã hết hạn hoặc chưa thực hiện yêu cầu đổi mật khẩu");
        }

        PendingPasswordChange pending;
        try {
            pending = objectMapper.readValue(json, PendingPasswordChange.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize pending password change", e);
        }

        if (!pending.getOtp().equals(request.getOtp())) {
            throw new InvalidOtpException("OTP không chính xác");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        user.setPassword(pending.getNewPasswordHash());
        userRepository.save(user);

        redisTemplate.delete(pendingKey(userId));
        // Buộc đăng xuất tất cả phiên để đảm bảo an toàn
        redisTemplate.delete(refreshTokenKey(phone));

        log.info("Password changed successfully for userId={}", userId);
    }

    private String pendingKey(String userId) {
        return redisNamespaceProperties.qualify(PENDING_PREFIX + userId);
    }

    private String refreshTokenKey(String phone) {
        return redisNamespaceProperties.qualify(REFRESH_TOKEN_PREFIX + phone);
    }
}
