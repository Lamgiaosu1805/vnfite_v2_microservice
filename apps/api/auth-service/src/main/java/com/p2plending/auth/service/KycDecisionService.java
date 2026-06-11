package com.p2plending.auth.service;

import com.p2plending.auth.domain.entity.KycSubmission;
import com.p2plending.auth.domain.entity.User;
import com.p2plending.auth.domain.enums.KycStatus;
import com.p2plending.auth.domain.repository.KycSubmissionRepository;
import com.p2plending.auth.domain.repository.UserRepository;
import com.p2plending.auth.exception.ResourceNotFoundException;
import com.p2plending.auth.kafka.KafkaProducerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Xử lý quyết định duyệt/từ chối KYC từ CMS admin.
 * Được gọi qua internal endpoint: PUT /internal/users/{userId}/kyc-decision
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KycDecisionService {

    private final UserRepository           userRepository;
    private final KycSubmissionRepository  kycSubmissionRepository;
    private final KafkaProducerService     kafkaProducerService;

    @Transactional
    public void decide(String userId, boolean approved, String reason) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User không tồn tại: " + userId));

        // Lấy submission PENDING mới nhất
        KycSubmission submission = kycSubmissionRepository
                .findTopByUserIdAndStatusOrderByCreatedAtDesc(userId, KycStatus.PENDING)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không có KYC PENDING nào của user: " + userId));

        if (approved) {
            submission.setStatus(KycStatus.APPROVED);
            user.setKycStatus(KycStatus.APPROVED);
            userRepository.save(user);
            kycSubmissionRepository.save(submission);

            // Publish event → payment-service sẽ tạo ví + VA
            kafkaProducerService.publishKycApproved(
                    userId,
                    submission.getFullName(),
                    submission.getCccdNumber());

            log.info("KYC APPROVED: userId={} submissionId={}", userId, submission.getId());

        } else {
            submission.setStatus(KycStatus.REJECTED);
            user.setKycStatus(KycStatus.NONE);  // reset về NONE để có thể submit lại
            userRepository.save(user);
            kycSubmissionRepository.save(submission);

            log.info("KYC REJECTED: userId={} submissionId={} reason={}",
                    userId, submission.getId(), reason);
        }
    }
}
