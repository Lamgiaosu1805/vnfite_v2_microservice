package com.p2plending.auth.domain.entity;

import com.p2plending.auth.domain.enums.AccountType;
import com.p2plending.auth.domain.enums.KycStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "users",
    indexes = {
        @Index(name = "idx_users_phone", columnList = "phone", unique = true),
        @Index(name = "idx_users_email", columnList = "email")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, unique = true, length = 20)
    private String phone;

    @Column(nullable = false)
    private String password;

    @Column(length = 150)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private KycStatus kycStatus = KycStatus.NONE;

    /**
     * Tier tài khoản sau khi hồ sơ doanh nghiệp được duyệt (INDIVIDUAL mặc định).
     * Chỉ mở khóa thêm nhóm sản phẩm BUSINESS/ENTERPRISE — không giới hạn quyền cá nhân.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    @Builder.Default
    private AccountType accountType = AccountType.INDIVIDUAL;

    @Column(length = 20)
    private String referredBy;

    @Column(name = "blacklisted", nullable = false)
    @Builder.Default
    private boolean blacklisted = false;

    @Column(name = "blacklisted_at")
    private LocalDateTime blacklistedAt;

    @Column(name = "blacklist_source", length = 50)
    private String blacklistSource;

    @Column(name = "blacklist_reason", length = 255)
    private String blacklistReason;

    /**
     * Public key (base64 X.509 SPKI) cho đăng nhập sinh trắc học theo challenge–response.
     * Private key tương ứng nằm trong Secure Enclave / Android Keystore của thiết bị,
     * không bao giờ rời máy. Server chỉ dùng key này để verify chữ ký challenge.
     * Null = chưa bật hoặc đã tắt sinh trắc học.
     */
    @Column(name = "biometric_public_key", columnDefinition = "TEXT")
    private String biometricPublicKey;

    @Column(nullable = false)
    @Builder.Default
    private boolean isDeleted = false;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
