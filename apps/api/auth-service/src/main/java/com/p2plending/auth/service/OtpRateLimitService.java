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
    private static final int MAX_CONSECUTIVE_REQUESTS = 2;
    private static final Duration REQUEST_WINDOW = Duration.ofSeconds(60);
    private static final Duration COOLDOWN = Duration.ofSeconds(60);

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

    private String requestCountKey(String phone) {
        return redisNamespaceProperties.qualify(REQUEST_COUNT_PREFIX + phone);
    }

    private String cooldownKey(String phone) {
        return redisNamespaceProperties.qualify(COOLDOWN_PREFIX + phone);
    }
}
