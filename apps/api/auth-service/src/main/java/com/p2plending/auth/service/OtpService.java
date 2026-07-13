package com.p2plending.auth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2plending.auth.config.RedisNamespaceProperties;
import com.p2plending.auth.domain.model.PendingRegistration;
import com.p2plending.auth.exception.InvalidOtpException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {

    private static final String PENDING_REG_PREFIX = "pending_reg:";
    private static final Duration OTP_TTL = Duration.ofMinutes(5);
    private static final String MOCK_OTP = "000000";

    @Value("${app.otp.mock:false}")
    private boolean mockMode;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper        objectMapper;
    private final OtpRateLimitService otpRateLimitService;
    private final RedisNamespaceProperties redisNamespaceProperties;
    private final VnfOtpSenderService vnfOtpSenderService;

    public String generateAndStore(PendingRegistration pending, String clientIp) {
        otpRateLimitService.assertCanRegisterRequest(pending.getPhone(), clientIp);

        String otp;
        if (mockMode) {
            otp = MOCK_OTP;
            log.info("[MOCK] OTP for {}: {}", pending.getPhone(), otp);
        } else {
            String sentOtp = vnfOtpSenderService.sendOtp(pending.getPhone(), VnfOtpSenderService.FN_REGISTER);
            if (sentOtp == null || sentOtp.isBlank()) {
                throw new InvalidOtpException("Không gửi được OTP đăng ký. Vui lòng thử lại sau.");
            }
            otp = sentOtp;
        }
        pending.setOtp(otp);

        try {
            redisTemplate.opsForValue().set(
                    pendingKey(pending.getPhone()),
                    objectMapper.writeValueAsString(pending),
                    OTP_TTL
            );
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize pending registration", e);
        }

        return otp;
    }

    public PendingRegistration verifyAndConsume(String phone, String otp) {
        otpRateLimitService.assertCanVerify(phone);
        String pendingKey = pendingKey(phone);
        String json = redisTemplate.opsForValue().get(pendingKey);
        if (json == null) {
            throw new InvalidOtpException("OTP đã hết hạn hoặc không tồn tại");
        }

        PendingRegistration pending;
        try {
            pending = objectMapper.readValue(json, PendingRegistration.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize pending registration", e);
        }

        if (!pending.getOtp().equals(otp)) {
            otpRateLimitService.recordFailedVerify(phone, pendingKey);
            throw new InvalidOtpException("OTP không chính xác");
        }

        otpRateLimitService.clearVerifyFailures(phone);
        redisTemplate.delete(pendingKey);
        return pending;
    }

    public boolean hasPending(String phone) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(pendingKey(phone)));
    }

    private String pendingKey(String phone) {
        return redisNamespaceProperties.qualify(PENDING_REG_PREFIX + phone);
    }
}
