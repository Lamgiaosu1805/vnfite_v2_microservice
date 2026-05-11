package com.p2plending.auth.domain.entity;

import com.p2plending.auth.domain.enums.Gender;
import com.p2plending.auth.domain.enums.KycStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "kyc_submissions",
    indexes = {
        @Index(name = "idx_kyc_sub_user",   columnList = "userId"),
        @Index(name = "idx_kyc_sub_cccd",   columnList = "cccdNumber", unique = true),
        @Index(name = "idx_kyc_sub_status", columnList = "status")
    }
)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class KycSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false, unique = true, length = 20)
    private String cccdNumber;

    @Column(nullable = false, length = 100)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Gender gender;

    @Column(nullable = false)
    private LocalDate dateOfBirth;

    @Column(nullable = false, length = 500)
    private String permanentAddress;

    @Column(nullable = false, length = 255)
    private String hometown;

    @Column(nullable = false)
    private LocalDate issueDate;

    @Column(nullable = false, length = 255)
    private String issuingAuthority;

    /** null = không thời hạn */
    private LocalDate expiryDate;

    /** ID trả về từ hệ thống lưu trữ ảnh bên ngoài */
    @Column(nullable = false, length = 255)
    private String frontImageId;

    @Column(nullable = false, length = 255)
    private String backImageId;

    @Column(nullable = false, length = 255)
    private String portraitImageId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private KycStatus status = KycStatus.PENDING;

    @Column(nullable = false)
    @Builder.Default
    private boolean isDeleted = false;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
