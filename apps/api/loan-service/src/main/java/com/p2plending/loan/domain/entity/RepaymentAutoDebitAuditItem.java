package com.p2plending.loan.domain.entity;

import com.p2plending.loan.domain.enums.AutoDebitLoanResultStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "repayment_auto_debit_audit_item",
    indexes = {
        @Index(name = "idx_auto_debit_item_audit", columnList = "auditId"),
        @Index(name = "idx_auto_debit_item_loan", columnList = "loanId"),
        @Index(name = "idx_auto_debit_item_borrower", columnList = "borrowerId"),
        @Index(name = "idx_auto_debit_item_status", columnList = "resultStatus")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepaymentAutoDebitAuditItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, length = 36)
    private String auditId;

    @Column(nullable = false, length = 36)
    private String loanId;

    @Column(length = 50)
    private String loanCode;

    @Column(length = 36)
    private String borrowerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AutoDebitLoanResultStatus resultStatus;

    @Column(nullable = false, precision = 15, scale = 0)
    @Builder.Default
    private BigDecimal amountCollected = BigDecimal.ZERO;

    @Column(length = 500)
    private String message;

    @Column(nullable = false)
    @Builder.Default
    private boolean isDeleted = false;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
