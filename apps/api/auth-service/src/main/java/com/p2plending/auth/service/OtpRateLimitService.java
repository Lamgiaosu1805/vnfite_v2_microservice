package com.p2plending.auth.service;

import com.p2plending.auth.config.RedisNamespaceProperties;
import com.p2plending.auth.exception.OtpRateLimitException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

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

    public void assertCanRequest(String phone) {
        if (!StringUtils.hasText(phone)) return;

        String subject = phone.trim();
        String cooldownKey = cooldownKey(subject);
        Long cooldownTtl = redisTemplate.getExpire(cooldownKey, TimeUnit.SECONDS);
        if (cooldownTtl != null && cooldownTtl > 0) {
            throw new OtpRateLimitException(cooldownTtl);
        }

        String countKey = requestCountKey(subject);
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

    private String requestCountKey(String phone) {
        return redisNamespaceProperties.qualify(REQUEST_COUNT_PREFIX + phone);
    }

    private String cooldownKey(String phone) {
        return redisNamespaceProperties.qualify(COOLDOWN_PREFIX + phone);
    }

    private String verifyFailKey(String subject) {
        return redisNamespaceProperties.qualify(VERIFY_FAIL_PREFIX + subject);
    }

    private String verifyCooldownKey(String subject) {
        return redisNamespaceProperties.qualify(VERIFY_COOLDOWN_PREFIX + subject);
    }
}
