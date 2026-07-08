package com.p2plending.cms.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(
    name = "cms_admin_users",
    indexes = {
        @Index(name = "idx_cms_admin_username", columnList = "username"),
        @Index(name = "idx_cms_admin_email", columnList = "email"),
        @Index(name = "idx_cms_admin_role", columnList = "role")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_cms_admin_username", columnNames = "username"),
        @UniqueConstraint(name = "uq_cms_admin_email", columnNames = "email")
    }
)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CmsAdminUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, length = 60)
    private String username;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(nullable = false, length = 100)
    private String fullName;

    @Column(nullable = false, length = 255)
    private String password;

    /** true = phải đổi MK ngay sau lần đăng nhập đầu tiên */
    @Column(nullable = false)
    @Builder.Default
    private boolean mustChangePassword = false;

    /** ID của admin tạo ra tài khoản này (null nếu là super admin đầu tiên) */
    @Column(length = 36)
    private String createdBy;

    /** Base32-encoded TOTP secret — null cho đến khi user thiết lập 2FA */
    @Column(length = 64)
    private String totpSecret;

    /** true = đã thiết lập và bật 2FA */
    @Column(nullable = false)
    @Builder.Default
    private boolean totpEnabled = false;

    /** Vai trò chính / nhãn hiển thị (SUPER_ADMIN | ADMIN | OPS | ...) — giữ để tương thích ngược. */
    @Column(nullable = false, length = 20)
    private String role;

    /**
     * Tập vai trò chi tiết theo phòng ban. Một tài khoản có thể mang nhiều vai trò.
     * EAGER vì bảng admin nhỏ và luôn cần vai trò khi phát hành JWT.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "cms_admin_user_roles",
        joinColumns = @JoinColumn(name = "user_id")
    )
    @Column(name = "role", nullable = false, length = 30)
    @Builder.Default
    private Set<String> roles = new HashSet<>();

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(nullable = false)
    @Builder.Default
    private boolean isDeleted = false;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
