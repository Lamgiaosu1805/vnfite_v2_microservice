package com.p2plending.loan.service;

import com.p2plending.loan.config.RedisNamespaceProperties;
import com.p2plending.loan.exception.OtpRateLimitException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Chống spam gửi OTP và brute-force xác thực OTP — dùng chung cho OTP tạo khoản gọi vốn
 * (LoanOtpService) và OTP ký hợp đồng điện tử (ContractService). Logic mirror
 * auth-service.OtpRateLimitService (mỗi service là module Maven riêng, không share code được).
 */
@Service
@RequiredArgsConstructor
public class OtpRateLimitService {

    private static final String REQUEST_COUNT_PREFIX = "otp_rate:count:";
    private static final String COOLDOWN_PREFIX = "otp_rate:cooldown:";
    private static final String VERIFY_FAIL_PREFIX = "otp_verify:fail:";
    private static final String VERIFY_COOLDOWN_PREFIX = "otp_verify:cooldown:";
    private static final int MAX_CONSECUTIVE_REQUESTS = 2;
    private static final int MAX_VERIFY_FAILURES = 5;
    private static final Duration REQUEST_WINDOW = Duration.ofSeconds(60);
    private static final Duration COOLDOWN = Duration.ofSeconds(60);
    private static final Duration VERIFY_FAIL_WINDOW = Duration.ofMinutes(5);
    private static final Duration VERIFY_COOLDOWN = Duration.ofMinutes(10);

    private final StringRedisTemplate redisTemplate;
    private final RedisNamespaceProperties redisNamespaceProperties;

    public void assertCanRequest(String subject) {
        if (!StringUtils.hasText(subject)) return;

        String normalized = subject.trim();
        String cooldownKey = cooldownKey(normalized);
        Long cooldownTtl = redisTemplate.getExpire(cooldownKey, TimeUnit.SECONDS);
        if (cooldownTtl != null && cooldownTtl > 0) {
            throw new OtpRateLimitException(cooldownTtl);
        }

        String countKey = requestCountKey(normalized);
        Long count = redisTemplate.opsForValue().increment(countKey);
        if (count == null) count = 1L;
        if (count == 1L) {
            redisTemplate.expire(countKey, REQUEST_WINDOW);
        }

        if (count > MAX_CONSECUTIVE_REQUESTS) {
            redisTemplate.opsForValue().set(cooldownKey, "1", COOLDOWN);
            throw new OtpRateLimitException(COOLDOWN.toSeconds());
        }

        if (count == MAX_CONSECUTIVE_REQUESTS) {
            redisTemplate.opsForValue().set(cooldownKey, "1", COOLDOWN);
        }
    }

    public void assertCanVerify(String subject) {
        if (!StringUtils.hasText(subject)) return;

        Long cooldownTtl = redisTemplate.getExpire(verifyCooldownKey(subject.trim()), TimeUnit.SECONDS);
        if (cooldownTtl != null && cooldownTtl > 0) {
            throw new OtpRateLimitException(
                    "Bạn đã nhập sai OTP quá nhiều lần. Vui lòng thử lại sau " + cooldownTtl + " giây.",
                    cooldownTtl);
        }
    }

    public void recordFailedVerify(String subject, String... keysToDeleteOnLockout) {
        if (!StringUtils.hasText(subject)) return;

        String normalized = subject.trim();
        String failKey = verifyFailKey(normalized);
        Long count = redisTemplate.opsForValue().increment(failKey);
        if (count == null) count = 1L;
        if (count == 1L) {
            redisTemplate.expire(failKey, VERIFY_FAIL_WINDOW);
        }

        if (count >= MAX_VERIFY_FAILURES) {
            redisTemplate.delete(failKey);
            redisTemplate.opsForValue().set(verifyCooldownKey(normalized), "1", VERIFY_COOLDOWN);
            if (keysToDeleteOnLockout != null) {
                for (String key : keysToDeleteOnLockout) {
                    if (StringUtils.hasText(key)) {
                        redisTemplate.delete(key);
                    }
                }
            }
            throw new OtpRateLimitException(
                    "Bạn đã nhập sai OTP quá nhiều lần. Vui lòng yêu cầu OTP mới sau "
                            + VERIFY_COOLDOWN.toSeconds() + " giây.",
                    VERIFY_COOLDOWN.toSeconds());
        }
    }

    public void clearVerifyFailures(String subject) {
        if (!StringUtils.hasText(subject)) return;
        redisTemplate.delete(verifyFailKey(subject.trim()));
    }

    private String requestCountKey(String subject) {
        return redisNamespaceProperties.qualify(REQUEST_COUNT_PREFIX + subject);
    }

    private String cooldownKey(String subject) {
        return redisNamespaceProperties.qualify(COOLDOWN_PREFIX + subject);
    }

    private String verifyFailKey(String subject) {
        return redisNamespaceProperties.qualify(VERIFY_FAIL_PREFIX + subject);
    }

    private String verifyCooldownKey(String subject) {
        return redisNamespaceProperties.qualify(VERIFY_COOLDOWN_PREFIX + subject);
    }
}
