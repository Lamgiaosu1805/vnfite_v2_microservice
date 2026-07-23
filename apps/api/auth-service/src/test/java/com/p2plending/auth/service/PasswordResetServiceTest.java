package com.p2plending.auth.service;

import com.p2plending.auth.config.RedisNamespaceProperties;
import com.p2plending.auth.domain.entity.User;
import com.p2plending.auth.domain.enums.KycStatus;
import com.p2plending.auth.domain.repository.KycSubmissionRepository;
import com.p2plending.auth.domain.repository.UserRepository;
import com.p2plending.auth.dto.request.ForgotPasswordRequest;
import com.p2plending.auth.exception.InvalidOtpException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {
    @Mock private UserRepository userRepository;
    @Mock private KycSubmissionRepository kycSubmissionRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private OtpRateLimitService otpRateLimitService;
    @Mock private VnfOtpSenderService vnfOtpSenderService;
    @InjectMocks private PasswordResetService passwordResetService;

    @Test
    void doesNotCreatePasswordResetOtpWhenGatewayCannotSend() {
        String phone = "0900000000";
        String clientIp = "203.0.113.8";
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setPhone(phone);
        ReflectionTestUtils.setField(passwordResetService, "mockMode", false);
        ReflectionTestUtils.setField(passwordResetService, "redisNamespaceProperties", new RedisNamespaceProperties());
        when(userRepository.findByPhone(phone)).thenReturn(Optional.of(User.builder().id("user-1").phone(phone).kycStatus(KycStatus.NONE).build()));
        when(vnfOtpSenderService.sendOtp(phone, VnfOtpSenderService.FN_FORGOT_PASSWORD)).thenReturn(null);

        assertThrows(InvalidOtpException.class, () -> passwordResetService.initReset(request, clientIp));

        verify(otpRateLimitService).assertCanRegisterRequest(phone, clientIp);
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void keepsExistingPasswordResetOtpWithoutCallingGateway() {
        String phone = "0900000000";
        String clientIp = "203.0.113.8";
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setPhone(phone);
        ReflectionTestUtils.setField(passwordResetService, "mockMode", false);
        ReflectionTestUtils.setField(passwordResetService, "redisNamespaceProperties", new RedisNamespaceProperties());
        when(userRepository.findByPhone(phone)).thenReturn(Optional.of(
                User.builder().id("user-1").phone(phone).kycStatus(KycStatus.NONE).build()));
        when(redisTemplate.hasKey("dev:auth-service:pwd_reset:" + phone)).thenReturn(true);

        var response = passwordResetService.initReset(request, clientIp);

        assertThat(response.get("message")).contains("vẫn còn hiệu lực");
        verify(otpRateLimitService, never()).assertCanRegisterRequest(phone, clientIp);
        verify(vnfOtpSenderService, never()).sendOtp(phone, VnfOtpSenderService.FN_FORGOT_PASSWORD);
        verify(redisTemplate, never()).opsForValue();
    }
}
