package com.p2plending.auth.domain.entity;

import com.p2plending.auth.domain.enums.DocType;
import com.p2plending.auth.domain.enums.KycStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "kyc_documents",
    indexes = {
        @Index(name = "idx_kyc_user_id", columnList = "userId"),
        @Index(name = "idx_kyc_status", columnList = "status")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KycDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DocType docType;

    @Column(nullable = false, length = 500)
    private String docUrl;

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
