package com.p2plending.matching.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Snapshot of loan data used by the scheduler for re-matching.
 * Populated from "loan.created" events; expired via "loan.funded".
 */
@Entity
@Table(
    name = "pending_loans",
    indexes = {
        @Index(name = "idx_pending_funded",       columnList = "fullyFunded"),
        @Index(name = "idx_pending_last_matched", columnList = "lastMatchedAt")
    }
)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class PendingLoan {

    @Id
    private String loanId;   // same as loan-service ID — no auto-generate

    @Column(nullable = false)
    private String borrowerId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal interestRate;

    @Column(nullable = false)
    private Integer termMonths;

    @Column(length = 500)
    private String purpose;

    @Column(nullable = false)
    @Builder.Default
    private boolean fullyFunded = false;

    @Column(nullable = false)
    @Builder.Default
    private boolean isDeleted = false;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime receivedAt;

    private LocalDateTime lastMatchedAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
