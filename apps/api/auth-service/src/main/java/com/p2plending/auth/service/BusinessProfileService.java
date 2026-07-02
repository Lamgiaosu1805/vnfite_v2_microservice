package com.p2plending.auth.service;

import com.p2plending.auth.domain.entity.BusinessProfile;
import com.p2plending.auth.domain.entity.KycSubmission;
import com.p2plending.auth.domain.entity.User;
import com.p2plending.auth.domain.enums.AccountType;
import com.p2plending.auth.domain.enums.BusinessType;
import com.p2plending.auth.domain.enums.KycStatus;
import com.p2plending.auth.domain.repository.BusinessProfileRepository;
import com.p2plending.auth.domain.repository.KycSubmissionRepository;
import com.p2plending.auth.domain.repository.UserRepository;
import com.p2plending.auth.dto.request.BusinessProfileSubmitRequest;
import com.p2plending.auth.dto.response.BusinessProfileResponse;
import com.p2plending.auth.dto.response.PagedResponse;
import com.p2plending.auth.exception.BusinessProfileConflictException;
import com.p2plending.auth.exception.InvalidIdentityException;
import com.p2plending.auth.exception.ResourceNotFoundException;
import com.p2plending.auth.kafka.KafkaProducerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.Optional;

/**
 * Hồ sơ doanh nghiệp — mô hình "1 tài khoản – 2 tư cách".
 *
 * <p>Nộp hồ sơ yêu cầu eKYC cá nhân APPROVED. Duyệt TAY trên CMS. Khi duyệt:
 * Hộ kinh doanh → account_type BUSINESS, Công ty → ENTERPRISE — chỉ MỞ THÊM nhóm sản phẩm
 * doanh nghiệp, không giới hạn quyền cá nhân. Publish {@code business-profile.approved} để
 * payment-service tạo ví doanh nghiệp riêng (giai đoạn B).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BusinessProfileService {

    private static final ZoneId TZ = ZoneId.of("Asia/Ho_Chi_Minh");

    private final BusinessProfileRepository businessProfileRepository;
    private final UserRepository            userRepository;
    private final KycSubmissionRepository   kycSubmissionRepository;
    private final ImageStorageService       imageStorageService;
    private final KafkaProducerService      kafkaProducerService;

    // ── Nộp hồ sơ (app) ──────────────────────────────────────────────

    @Transactional
    public BusinessProfileResponse submit(String userId, BusinessProfileSubmitRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        if (user.getKycStatus() != KycStatus.APPROVED) {
            throw new InvalidIdentityException(
                    "Cần hoàn tất xác minh danh tính cá nhân (eKYC) trước khi đăng ký hồ sơ doanh nghiệp");
        }
        if (businessProfileRepository.existsByUserIdAndStatusInAndIsDeletedFalse(
                userId, EnumSet.of(KycStatus.PENDING, KycStatus.APPROVED))) {
            throw new BusinessProfileConflictException(
                    "Bạn đã có hồ sơ doanh nghiệp đang chờ duyệt hoặc đã được duyệt");
        }

        // Nộp lại sau khi bị từ chối: soft-delete hồ sơ REJECTED cũ để giữ đúng 1 hồ sơ active
        businessProfileRepository.findTopByUserIdAndIsDeletedFalseOrderByCreatedAtDesc(userId)
                .filter(p -> p.getStatus() == KycStatus.REJECTED)
                .ifPresent(old -> {
                    old.setDeleted(true);
                    businessProfileRepository.save(old);
                });

        String licenseImageId = imageStorageService.upload(request.getLicenseImage());
        String extra1 = request.getLicenseExtra1Image() != null && !request.getLicenseExtra1Image().isEmpty()
                ? imageStorageService.upload(request.getLicenseExtra1Image()) : null;
        String extra2 = request.getLicenseExtra2Image() != null && !request.getLicenseExtra2Image().isEmpty()
                ? imageStorageService.upload(request.getLicenseExtra2Image()) : null;

        BusinessProfile profile = businessProfileRepository.save(BusinessProfile.builder()
                .userId(userId)
                .businessType(request.getBusinessType())
                .businessName(request.getBusinessName().trim())
                .registrationNumber(request.getRegistrationNumber().trim())
                .taxCode(blankToNull(request.getTaxCode()))
                .issueDate(request.getIssueDate())
                .issuedBy(blankToNull(request.getIssuedBy()))
                .headOfficeAddress(request.getHeadOfficeAddress().trim())
                .businessSector(blankToNull(request.getBusinessSector()))
                .representativeName(request.getRepresentativeName().trim())
                .representativeCccd(request.getRepresentativeCccd().trim())
                .licenseImageId(licenseImageId)
                .licenseExtra1ImageId(extra1)
                .licenseExtra2ImageId(extra2)
                .status(KycStatus.PENDING)
                .build());

        log.info("Business profile submitted: userId={} profileId={} type={}",
                userId, profile.getId(), profile.getBusinessType());
        return toResponse(profile, ekycCccdOf(userId));
    }

    // ── Xem hồ sơ của chính mình (app) ───────────────────────────────

    @Transactional(readOnly = true)
    public Optional<BusinessProfileResponse> getMine(String userId) {
        return businessProfileRepository.findTopByUserIdAndIsDeletedFalseOrderByCreatedAtDesc(userId)
                .map(p -> toResponse(p, ekycCccdOf(userId)));
    }

    // ── CMS: danh sách + chi tiết ────────────────────────────────────

    @Transactional(readOnly = true)
    public PagedResponse<BusinessProfileResponse> list(KycStatus status, int page, int size) {
        PageRequest pageable = PageRequest.of(page, Math.min(size, 100),
                Sort.by("createdAt").descending());
        Page<BusinessProfile> result = status != null
                ? businessProfileRepository.findByStatusAndIsDeletedFalse(status, pageable)
                : businessProfileRepository.findByIsDeletedFalse(pageable);
        return PagedResponse.<BusinessProfileResponse>builder()
                .content(result.getContent().stream()
                        .map(p -> toResponse(p, ekycCccdOf(p.getUserId())))
                        .toList())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .last(result.isLast())
                .build();
    }

    @Transactional(readOnly = true)
    public BusinessProfileResponse getByUserId(String userId) {
        BusinessProfile profile = businessProfileRepository
                .findTopByUserIdAndIsDeletedFalseOrderByCreatedAtDesc(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy hồ sơ doanh nghiệp của user: " + userId));
        return toResponse(profile, ekycCccdOf(userId));
    }

    // ── CMS: quyết định duyệt/từ chối ────────────────────────────────

    @Transactional
    public void decide(String userId, boolean approved, String reason, String reviewedBy) {
        BusinessProfile profile = businessProfileRepository
                .findTopByUserIdAndIsDeletedFalseOrderByCreatedAtDesc(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy hồ sơ doanh nghiệp của user: " + userId));
        if (profile.getStatus() != KycStatus.PENDING) {
            throw new BusinessProfileConflictException(
                    "Hồ sơ không ở trạng thái chờ duyệt (hiện tại: " + profile.getStatus() + ")");
        }

        profile.setReviewedBy(reviewedBy);
        profile.setReviewedAt(LocalDateTime.now(TZ));

        if (approved) {
            profile.setStatus(KycStatus.APPROVED);
            profile.setRejectReason(null);
            businessProfileRepository.save(profile);

            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
            user.setAccountType(profile.getBusinessType() == BusinessType.HOUSEHOLD
                    ? AccountType.BUSINESS : AccountType.ENTERPRISE);
            userRepository.save(user);

            kafkaProducerService.publishBusinessProfileApproved(
                    userId, profile.getBusinessName(), profile.getBusinessType().name(),
                    profile.getRegistrationNumber(), profile.getTaxCode());

            log.info("Business profile approved: userId={} profileId={} accountType={} by={}",
                    userId, profile.getId(), user.getAccountType(), reviewedBy);
        } else {
            profile.setStatus(KycStatus.REJECTED);
            profile.setRejectReason(reason);
            businessProfileRepository.save(profile);
            log.info("Business profile rejected: userId={} profileId={} reason={} by={}",
                    userId, profile.getId(), reason, reviewedBy);
        }
    }

    // ── CMS: lưu kết quả AI tham khảo ────────────────────────────────

    @Transactional
    public void saveAiResult(String userId, String verdict, String summary) {
        businessProfileRepository.findTopByUserIdAndIsDeletedFalseOrderByCreatedAtDesc(userId)
                .ifPresent(profile -> {
                    profile.setAiVerdict(verdict);
                    profile.setAiSummary(summary);
                    businessProfileRepository.save(profile);
                });
    }

    // ── Helper ───────────────────────────────────────────────────────

    /** CCCD trên eKYC của chủ tài khoản — dùng đối chiếu với CCCD người đại diện. */
    private String ekycCccdOf(String userId) {
        return kycSubmissionRepository
                .findTopByUserIdAndStatusOrderByCreatedAtDesc(userId, KycStatus.APPROVED)
                .map(KycSubmission::getCccdNumber)
                .orElse(null);
    }

    private BusinessProfileResponse toResponse(BusinessProfile p, String ekycCccd) {
        return BusinessProfileResponse.builder()
                .id(p.getId())
                .userId(p.getUserId())
                .businessType(p.getBusinessType())
                .businessName(p.getBusinessName())
                .registrationNumber(p.getRegistrationNumber())
                .taxCode(p.getTaxCode())
                .issueDate(p.getIssueDate())
                .issuedBy(p.getIssuedBy())
                .headOfficeAddress(p.getHeadOfficeAddress())
                .businessSector(p.getBusinessSector())
                .representativeName(p.getRepresentativeName())
                .representativeCccd(p.getRepresentativeCccd())
                .licenseImageId(p.getLicenseImageId())
                .licenseExtra1ImageId(p.getLicenseExtra1ImageId())
                .licenseExtra2ImageId(p.getLicenseExtra2ImageId())
                .status(p.getStatus())
                .rejectReason(p.getRejectReason())
                .aiVerdict(p.getAiVerdict())
                .aiSummary(p.getAiSummary())
                .reviewedBy(p.getReviewedBy())
                .reviewedAt(p.getReviewedAt())
                .createdAt(p.getCreatedAt())
                .representativeMismatch(ekycCccd != null && !ekycCccd.equals(p.getRepresentativeCccd()))
                .build();
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
