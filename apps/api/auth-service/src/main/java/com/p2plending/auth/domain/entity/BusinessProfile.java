package com.p2plending.auth.domain.entity;

import com.p2plending.auth.domain.enums.BusinessType;
import com.p2plending.auth.domain.enums.KycStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Hồ sơ doanh nghiệp — lớp xác minh BỔ SUNG gắn vào tài khoản cá nhân đã eKYC.
 *
 * <p>Nộp yêu cầu user đã eKYC APPROVED. Duyệt TAY trên CMS (AI đọc GPKD chỉ tham khảo).
 * Mỗi user tối đa 1 hồ sơ active (PENDING/APPROVED) — guard ở service; hồ sơ REJECTED
 * được soft-delete khi nộp lại nên không cần unique cứng.
 */
@Entity
@Table(
    name = "business_profiles",
    indexes = {
        @Index(name = "idx_business_profiles_user", columnList = "user_id"),
        @Index(name = "idx_business_profiles_status", columnList = "status")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "business_type", nullable = false, length = 20)
    private BusinessType businessType;

    @Column(name = "business_name", nullable = false, length = 255)
    private String businessName;

    /** Số GCN đăng ký doanh nghiệp / GCN đăng ký hộ kinh doanh. */
    @Column(name = "registration_number", nullable = false, length = 50)
    private String registrationNumber;

    /** MST — hộ kinh doanh có thể chưa có. */
    @Column(name = "tax_code", length = 20)
    private String taxCode;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "issued_by", length = 255)
    private String issuedBy;

    @Column(name = "head_office_address", nullable = false, length = 500)
    private String headOfficeAddress;

    @Column(name = "business_sector", length = 255)
    private String businessSector;

    /** Người đại diện pháp luật / chủ hộ — đối chiếu với eKYC của user. */
    @Column(name = "representative_name", nullable = false, length = 100)
    private String representativeName;

    @Column(name = "representative_cccd", nullable = false, length = 20)
    private String representativeCccd;

    @Column(name = "license_image_id", nullable = false, length = 255)
    private String licenseImageId;

    @Column(name = "license_extra1_image_id", length = 255)
    private String licenseExtra1ImageId;

    @Column(name = "license_extra2_image_id", length = 255)
    private String licenseExtra2ImageId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private KycStatus status = KycStatus.PENDING;

    @Column(name = "reject_reason", length = 500)
    private String rejectReason;

    /** Verdict AI đọc GPKD gần nhất — chỉ tham khảo, không auto-approve. */
    @Column(name = "ai_verdict", length = 30)
    private String aiVerdict;

    @Column(name = "ai_summary", columnDefinition = "TEXT")
    private String aiSummary;

    @Column(name = "reviewed_by", length = 100)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean isDeleted = false;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
