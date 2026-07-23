package com.p2plending.auth.service;

import com.p2plending.auth.config.RedisNamespaceProperties;
import com.p2plending.auth.exception.OtpRateLimitException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OtpRateLimitServiceTest {
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final OtpIpBlockService ipBlockService = mock(OtpIpBlockService.class);
    private final OtpRateLimitService service = new OtpRateLimitService(
            redisTemplate, new RedisNamespaceProperties(), ipBlockService);

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void secondIpStrikeCreatesPermanentBlock() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.getExpire(anyString(), eq(TimeUnit.SECONDS))).thenReturn(-1L);
        when(valueOperations.increment(anyString())).thenAnswer(invocation ->
                invocation.getArgument(0, String.class).contains(":strikes:") ? 2L : 1L);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(-6L);

        assertThatThrownBy(() -> service.assertCanRegisterRequest("0900000000", "203.0.113.8"))
                .isInstanceOf(OtpRateLimitException.class);

        verify(ipBlockService).blockAutomatically(
                "203.0.113.8", "Tự động chặn do tái phạm xoay nhiều số điện thoại để gửi OTP");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void deviceRotatingMoreThanThreePhonesIsRejected() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(-2L);

        assertThatThrownBy(() -> service.assertCanRequestFromDevice(
                "0900000004", "2cb5525a-f7ad-4a10-8ef7-71ad923bb1f2"))
                .isInstanceOf(OtpRateLimitException.class)
                .hasMessageContaining("quá nhiều số điện thoại");
    }
}
