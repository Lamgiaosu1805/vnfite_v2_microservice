package com.p2plending.auth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2plending.auth.config.RedisNamespaceProperties;
import com.p2plending.auth.domain.entity.KycSubmission;
import com.p2plending.auth.domain.entity.User;
import com.p2plending.auth.domain.enums.KycStatus;
import com.p2plending.auth.domain.model.PendingKycData;
import com.p2plending.auth.domain.repository.KycSubmissionRepository;
import com.p2plending.auth.domain.repository.UserRepository;
import com.p2plending.auth.dto.request.KycInitRequest;
import com.p2plending.auth.dto.request.KycVerifyRequest;
import com.p2plending.auth.dto.response.KycSubmissionResponse;
import com.p2plending.auth.exception.DuplicateCccdException;
import com.p2plending.auth.exception.InvalidOtpException;
import com.p2plending.auth.exception.ResourceNotFoundException;
import com.p2plending.auth.kafka.KafkaProducerService;
import com.p2plending.auth.service.vwork.CustomerSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class KycService {

    private static final String KYC_PENDING_PREFIX = "kyc_pending:";
    private static final Duration KYC_OTP_TTL = Duration.ofMinutes(10);
    private static final String MOCK_OTP = "000000";

    @Value("${app.otp.mock:false}")
    private boolean mockMode;

    private final UserRepository           userRepository;
    private final KycSubmissionRepository  kycSubmissionRepository;
    private final ImageStorageService      imageStorageService;
    private final KafkaProducerService     kafkaProducerService;
    private final StringRedisTemplate      redisTemplate;
    private final ObjectMapper             objectMapper;
    private final OtpRateLimitService      otpRateLimitService;
    private final RedisNamespaceProperties redisNamespaceProperties;
    private final VnfOtpSenderService      vnfOtpSenderService;
    private final CustomerSyncService      customerSyncService;

    @Value("${spring.vwork.api-key}")
    private String apiKey;

    // ── Bước 1: kiểm tra CCCD, upload ảnh mock, gửi OTP ─────────────

    @Transactional(readOnly = true)
    public Map<String, String> initKyc(String userId, String phone, KycInitRequest request) {
        otpRateLimitService.assertCanRequest(phone);

        if (kycSubmissionRepository.existsByCccdNumber(request.getCccdNumber())) {
            throw new DuplicateCccdException("Số CCCD đã được đăng ký trong hệ thống");
        }

        String frontImageId    = imageStorageService.upload(request.getFrontImage());
        String backImageId     = imageStorageService.upload(request.getBackImage());
        String portraitImageId = imageStorageService.upload(request.getPortraitImage());

        String otp;
        if (mockMode) {
            otp = MOCK_OTP;
        } else {
            String sentOtp = vnfOtpSenderService.sendOtp(phone, VnfOtpSenderService.FN_KYC);
            otp = (sentOtp != null) ? sentOtp : String.format("%06d", new SecureRandom().nextInt(1_000_000));
        }

        PendingKycData pending = new PendingKycData(
                userId,
                request.getCccdNumber(),
                request.getFullName(),
                request.getGender(),
                request.getDateOfBirth(),
                request.getPermanentAddress(),
                request.getHometown(),
                request.getIssueDate(),
                request.getIssuingAuthority(),
                request.getExpiryDate(),
                frontImageId,
                backImageId,
                portraitImageId,
                otp
        );

        try {
            redisTemplate.opsForValue().set(
                    pendingKey(userId),
                    objectMapper.writeValueAsString(pending),
                    KYC_OTP_TTL
            );
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Lỗi lưu trữ thông tin KYC tạm thời", e);
        }

        log.info("KYC init: userId={}", userId);

        return Map.of("message", "OTP đã được gửi đến số điện thoại của bạn");
    }

    // ── Bước 2: xác thực OTP, lưu KYC ──────────────────────────────

    @Transactional
    public KycSubmissionResponse verifyKyc(String userId, KycVerifyRequest request) {
        otpRateLimitService.assertCanVerify(userId);
        String pendingKey = pendingKey(userId);
        String json = redisTemplate.opsForValue().get(pendingKey);
        if (json == null) {
            throw new InvalidOtpException("OTP đã hết hạn hoặc chưa thực hiện bước khởi tạo KYC");
        }

        PendingKycData pending;
        try {
            pending = objectMapper.readValue(json, PendingKycData.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Lỗi đọc thông tin KYC tạm thời", e);
        }

        if (!pending.getOtp().equals(request.getOtp())) {
            otpRateLimitService.recordFailedVerify(userId, pendingKey);
            throw new InvalidOtpException("OTP không chính xác");
        }

        otpRateLimitService.clearVerifyFailures(userId);
        redisTemplate.delete(pendingKey);

        KycSubmission submission = KycSubmission.builder()
                .userId(userId)
                .cccdNumber(pending.getCccdNumber())
                .fullName(pending.getFullName())
                .gender(pending.getGender())
                .dateOfBirth(pending.getDateOfBirth())
                .permanentAddress(pending.getPermanentAddress())
                .hometown(pending.getHometown())
                .issueDate(pending.getIssueDate())
                .issuingAuthority(pending.getIssuingAuthority())
                .expiryDate(pending.getExpiryDate())
                .frontImageId(pending.getFrontImageId())
                .backImageId(pending.getBackImageId())
                .portraitImageId(pending.getPortraitImageId())
                .status(KycStatus.APPROVED)  // eKYC tự động xác minh, không cần CMS duyệt
                .build();

        KycSubmission saved = kycSubmissionRepository.save(submission);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        user.setKycStatus(KycStatus.APPROVED);
        userRepository.save(user);

        // Call VWork
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        customerSyncService.syncEKYC(apiKey, userId, user.getPhone(), submission);
                    }
                }
        );

        // Notify analytics/notification về submission
        kafkaProducerService.publishKycSubmitted(userId, saved.getId());

        // Trigger tạo ví — payment-service lắng nghe event này
        kafkaProducerService.publishKycApproved(userId, saved.getFullName(), saved.getCccdNumber());

        log.info("KYC verified: userId={} submissionId={}", userId, saved.getId());
        return toResponse(saved);
    }

    // ── Helper ───────────────────────────────────────────────────────

    private String pendingKey(String userId) {
        return redisNamespaceProperties.qualify(KYC_PENDING_PREFIX + userId);
    }

    private KycSubmissionResponse toResponse(KycSubmission s) {
        return KycSubmissionResponse.builder()
                .id(s.getId())
                .userId(s.getUserId())
                .cccdNumber(s.getCccdNumber())
                .fullName(s.getFullName())
                .gender(s.getGender())
                .dateOfBirth(s.getDateOfBirth())
                .permanentAddress(s.getPermanentAddress())
                .hometown(s.getHometown())
                .issueDate(s.getIssueDate())
                .issuingAuthority(s.getIssuingAuthority())
                .expiryDate(s.getExpiryDate())
                .frontImageId(s.getFrontImageId())
                .backImageId(s.getBackImageId())
                .portraitImageId(s.getPortraitImageId())
                .status(s.getStatus())
                .createdAt(s.getCreatedAt())
                .build();
    }
}
