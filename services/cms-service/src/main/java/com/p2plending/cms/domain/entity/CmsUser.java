package com.p2plending.cms.domain.entity;

import com.p2plending.cms.domain.enums.UserAccountStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "cms_users",
    indexes = {
        @Index(name = "idx_cu_email",      columnList = "email"),
        @Index(name = "idx_cu_kycstatus",  columnList = "kycStatus"),
        @Index(name = "idx_cu_role",       columnList = "role"),
        @Index(name = "idx_cu_status",     columnList = "accountStatus")
    }
)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CmsUser {

    @Id
    private Long userId;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(nullable = false, length = 100)
    private String fullName;

    @Column(length = 20)
    private String phone;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String kycStatus = "NONE";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private UserAccountStatus accountStatus = UserAccountStatus.ACTIVE;

    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
