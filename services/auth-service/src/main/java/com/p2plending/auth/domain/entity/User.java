package com.p2plending.auth.domain.entity;

import com.p2plending.auth.domain.enums.KycStatus;
import com.p2plending.auth.domain.enums.Role;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "users",
    indexes = {
        @Index(name = "idx_users_phone",         columnList = "phone",        unique = true),
        @Index(name = "idx_users_referral_code", columnList = "referralCode", unique = true),
        @Index(name = "idx_users_email",         columnList = "email")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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
    private Role role = Role.BORROWER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private KycStatus kycStatus = KycStatus.NONE;

    @Column(nullable = false, unique = true, length = 10)
    private String referralCode;

    @Column(length = 10)
    private String referredBy;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
