package com.p2plending.payment.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "reconciliation_items")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "session_id", nullable = false, length = 36)
    private String sessionId;

    /** STALE_DEPOSIT | STALE_WITHDRAWAL | WITHDRAWAL_MB_MISMATCH | FAILED_WITHDRAWAL_MB_SUCCESS */
    @Column(name = "item_type", nullable = false, length = 60)
    private String itemType;

    /** HIGH | MEDIUM | LOW */
    @Column(name = "severity", nullable = false, length = 10)
    @Builder.Default
    private String severity = "MEDIUM";

    @Column(name = "wallet_id", length = 36)
    private String walletId;

    @Column(name = "transaction_id", length = 36)
    private String transactionId;

    @Column(name = "reference_id", length = 200)
    private String referenceId;

    @Column(name = "external_ref", length = 200)
    private String externalRef;

    @Column(name = "vnfite_status", length = 30)
    private String vnfiteStatus;

    @Column(name = "mb_status", length = 50)
    private String mbStatus;

    @Column(name = "amount", precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** OPEN | INVESTIGATING | RESOLVED */
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "OPEN";

    @Column(name = "resolved_by", length = 100)
    private String resolvedBy;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private boolean isDeleted = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
