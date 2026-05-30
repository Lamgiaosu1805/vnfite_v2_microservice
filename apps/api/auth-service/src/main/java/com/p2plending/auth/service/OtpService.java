package com.p2plending.auth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2plending.auth.domain.model.PendingRegistration;
import com.p2plending.auth.exception.InvalidOtpException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
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

    public String generateAndStore(PendingRegistration pending) {
        String otp = mockMode ? MOCK_OTP : String.format("%06d", new SecureRandom().nextInt(1_000_000));
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

        if (mockMode) {
            log.info("[MOCK] OTP for {}: {}", pending.getPhone(), otp);
        } else {
            // TODO: send via SMS / Zalo ZNS
            log.info("OTP sent to {}", pending.getPhone());
        }

        return otp;
    }

    public PendingRegistration verifyAndConsume(String phone, String otp) {
        String json = redisTemplate.opsForValue().get(pendingKey(phone));
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
            throw new InvalidOtpException("OTP không chính xác");
        }

        redisTemplate.delete(pendingKey(phone));
        return pending;
    }

    public boolean hasPending(String phone) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(pendingKey(phone)));
    }

    private String pendingKey(String phone) {
        return PENDING_REG_PREFIX + phone;
    }
}
