package com.p2plending.auth.service;

import com.p2plending.auth.config.RedisNamespaceProperties;
import com.p2plending.auth.domain.entity.KycSubmission;
import com.p2plending.auth.domain.entity.User;
import com.p2plending.auth.domain.enums.KycStatus;
import com.p2plending.auth.domain.repository.KycSubmissionRepository;
import com.p2plending.auth.domain.repository.UserRepository;
import com.p2plending.auth.exception.InvalidOtpException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceResetServiceTest {
    @Mock private UserRepository userRepository;
    @Mock private KycSubmissionRepository kycSubmissionRepository;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private OtpRateLimitService otpRateLimitService;
    @Mock private VnfOtpSenderService vnfOtpSenderService;
    @InjectMocks private AuthService authService;

    @Test
    void doesNotCreateDeviceResetOtpWhenGatewayCannotSend() {
        String phone = "0900000000";
        String cccd = "001234567890";
        String issueDate = "2021-12-22";
        String clientIp = "203.0.113.8";
        ReflectionTestUtils.setField(authService, "mockMode", false);
        ReflectionTestUtils.setField(authService, "redisNamespaceProperties", new RedisNamespaceProperties());
        when(userRepository.findByPhone(phone)).thenReturn(Optional.of(User.builder().id("user-1").phone(phone).build()));
        when(kycSubmissionRepository.findTopByUserIdAndStatusOrderByCreatedAtDesc("user-1", KycStatus.APPROVED))
                .thenReturn(Optional.of(KycSubmission.builder().cccdNumber(cccd).issueDate(LocalDate.parse(issueDate)).build()));
        when(vnfOtpSenderService.sendOtp(phone, VnfOtpSenderService.FN_DEVICE_RESET)).thenReturn(null);

        assertThrows(InvalidOtpException.class, () -> authService.initDeviceReset(phone, cccd, issueDate, clientIp));

        verify(otpRateLimitService).assertCanRegisterRequest(phone, clientIp);
        verify(redisTemplate, never()).opsForValue();
    }
}
