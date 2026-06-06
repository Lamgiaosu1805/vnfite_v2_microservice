package com.p2plending.auth.service;

import com.p2plending.auth.config.RedisNamespaceProperties;
import com.p2plending.auth.domain.entity.User;
import com.p2plending.auth.domain.enums.KycStatus;
import com.p2plending.auth.domain.repository.KycSubmissionRepository;
import com.p2plending.auth.domain.repository.UserRepository;
import com.p2plending.auth.dto.request.ForgotPasswordCheckRequest;
import com.p2plending.auth.dto.request.ForgotPasswordOtpVerifyRequest;
import com.p2plending.auth.dto.request.ForgotPasswordRequest;
import com.p2plending.auth.dto.request.ForgotPasswordResetRequest;
import com.p2plending.auth.exception.InvalidIdentityException;
import com.p2plending.auth.exception.InvalidOtpException;
import com.p2plending.auth.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {

    private static final String OTP_PREFIX          = "pwd_reset:";
    private static final String RESET_TOKEN_PREFIX  = "pwd_reset_token:";
    private static final String REFRESH_TOKEN_PREFIX = "refresh_token:";
    private static final Duration OTP_TTL           = Duration.ofMinutes(5);
    private static final Duration RESET_TOKEN_TTL   = Duration.ofMinutes(10);
    private static final String MOCK_OTP            = "000000";

    @Value("${app.otp.mock:false}")
    private boolean mockMode;

    private final UserRepository          userRepository;
    private final KycSubmissionRepository kycSubmissionRepository;
    private final PasswordEncoder         passwordEncoder;
    private final StringRedisTemplate     redisTemplate;
    private final OtpRateLimitService     otpRateLimitService;
    private final RedisNamespaceProperties redisNamespaceProperties;

    // ── Bước 0: kiểm tra phone → frontend biết có cần hiện ô CCCD không ──

    @Transactional(readOnly = true)
    public Map<String, Object> checkPhone(ForgotPasswordCheckRequest request) {
        boolean requiresCccd = userRepository.findByPhone(request.getPhone())
                .map(user -> user.getKycStatus() != KycStatus.NONE)
                .orElse(false);
        return Map.of("requiresCccd", requiresCccd);
    }

    // ── Bước 1: xác minh danh tính, gửi OTP ──────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, String> initReset(ForgotPasswordRequest request) {
        otpRateLimitService.assertCanRequest(request.getPhone());

        Optional<User> userOpt = userRepository.findByPhone(request.getPhone());

        if (userOpt.isEmpty()) {
            log.info("Password reset requested for unknown phone={}", request.getPhone());
            return genericOkResponse(null);
        }

        User user = userOpt.get();

        if (user.getKycStatus() != KycStatus.NONE) {
            if (!StringUtils.hasText(request.getCccdNumber())) {
                throw new InvalidIdentityException(
                        "Tài khoản đã xác minh danh tính. Vui lòng nhập số CCCD để tiếp tục.");
            }
            if (!kycSubmissionRepository.existsByUserIdAndCccdNumber(user.getId(), request.getCccdNumber())) {
                throw new InvalidIdentityException("Số CCCD không khớp với thông tin đã đăng ký");
            }
        }

        String otp = mockMode ? MOCK_OTP
                : String.format("%06d", new SecureRandom().nextInt(1_000_000));
        redisTemplate.opsForValue().set(otpKey(request.getPhone()), otp, OTP_TTL);

        if (mockMode) {
            log.info("[MOCK] Password reset OTP for phone={}: {}", request.getPhone(), otp);
        } else {
            // TODO: gửi OTP qua SMS / Zalo ZNS
            log.info("Password reset OTP sent for phone={}", request.getPhone());
        }

        return genericOkResponse(mockMode ? otp : null);
    }

    // ── Bước 2: xác thực OTP → trả resetToken, chuyển sang màn nhập MK mới

    public Map<String, String> verifyOtp(ForgotPasswordOtpVerifyRequest request) {
        String stored = redisTemplate.opsForValue().get(otpKey(request.getPhone()));
        if (stored == null) {
            throw new InvalidOtpException("OTP đã hết hạn hoặc chưa thực hiện yêu cầu đặt lại mật khẩu");
        }
        if (!stored.equals(request.getOtp())) {
            throw new InvalidOtpException("OTP không chính xác");
        }

        redisTemplate.delete(otpKey(request.getPhone()));

        String resetToken = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(resetTokenKey(resetToken), request.getPhone(), RESET_TOKEN_TTL);

        log.info("OTP verified for phone={}, reset token issued", request.getPhone());
        return Map.of("resetToken", resetToken);
    }

    // ── Bước 3: dùng resetToken để đặt mật khẩu mới ─────────────────────

    @Transactional
    public void resetPassword(ForgotPasswordResetRequest request) {
        String phone = redisTemplate.opsForValue().get(resetTokenKey(request.getResetToken()));
        if (phone == null) {
            throw new InvalidOtpException("Phiên đặt lại mật khẩu đã hết hạn. Vui lòng thực hiện lại.");
        }

        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // Vô hiệu hoá resetToken và buộc đăng xuất tất cả phiên
        redisTemplate.delete(resetTokenKey(request.getResetToken()));
        redisTemplate.delete(refreshTokenKey(phone));

        log.info("Password reset successful for userId={}", user.getId());
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private String otpKey(String phone) {
        return redisNamespaceProperties.qualify(OTP_PREFIX + phone);
    }

    private String resetTokenKey(String token) {
        return redisNamespaceProperties.qualify(RESET_TOKEN_PREFIX + token);
    }

    private String refreshTokenKey(String phone) {
        return redisNamespaceProperties.qualify(REFRESH_TOKEN_PREFIX + phone);
    }

    private Map<String, String> genericOkResponse(String otp) {
        Map<String, String> res = new HashMap<>();
        res.put("message", "Nếu số điện thoại tồn tại, OTP sẽ được gửi đến điện thoại của bạn");
        if (otp != null) res.put("otp", otp);
        return res;
    }
}
