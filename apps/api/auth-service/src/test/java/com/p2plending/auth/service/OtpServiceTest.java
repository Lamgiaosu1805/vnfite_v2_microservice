package com.p2plending.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2plending.auth.config.RedisNamespaceProperties;
import com.p2plending.auth.domain.model.PendingRegistration;
import com.p2plending.auth.exception.InvalidOtpException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private OtpRateLimitService otpRateLimitService;

    @Mock
    private VnfOtpSenderService vnfOtpSenderService;

    @InjectMocks
    private OtpService otpService;

    @Test
    void doesNotCreatePendingRegistrationWhenGatewayCannotSendOtp() {
        ReflectionTestUtils.setField(otpService, "mockMode", false);
        ReflectionTestUtils.setField(otpService, "redisNamespaceProperties", new RedisNamespaceProperties());
        ReflectionTestUtils.setField(otpService, "objectMapper", new ObjectMapper());
        when(vnfOtpSenderService.sendOtp("0900000000", VnfOtpSenderService.FN_REGISTER)).thenReturn(null);

        assertThrows(InvalidOtpException.class, () -> otpService.generateAndStore(
                new PendingRegistration("0900000000", "hash", null, null, null), "203.0.113.8"));

        verify(otpRateLimitService).assertCanRegisterRequest("0900000000", "203.0.113.8");
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void keepsExistingRegistrationOtpWithoutCallingGateway() {
        ReflectionTestUtils.setField(otpService, "mockMode", false);
        ReflectionTestUtils.setField(otpService, "redisNamespaceProperties", new RedisNamespaceProperties());
        ReflectionTestUtils.setField(otpService, "objectMapper", new ObjectMapper());
        when(redisTemplate.hasKey("dev:auth-service:pending_reg:0900000000")).thenReturn(true);

        String result = otpService.generateAndStore(
                new PendingRegistration("0900000000", "hash", null, null, null), "203.0.113.8");

        assertThat(result).isEmpty();
        verify(otpRateLimitService, never()).assertCanRegisterRequest(any(), any());
        verify(vnfOtpSenderService, never()).sendOtp(any(), anyInt());
        verify(redisTemplate, never()).opsForValue();
    }
}
