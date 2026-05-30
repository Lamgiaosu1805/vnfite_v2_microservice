package com.p2plending.auth.domain.entity;

import com.p2plending.auth.domain.enums.KycStatus;
import com.p2plending.auth.domain.enums.Role;
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

    @Column(length = 100)
    private String fullName;

    @Column(length = 150)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Role role = Role.USER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private KycStatus kycStatus = KycStatus.NONE;

    @Column(length = 20)
    private String referredBy;

    @Column(nullable = false)
    @Builder.Default
    private boolean isDeleted = false;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
