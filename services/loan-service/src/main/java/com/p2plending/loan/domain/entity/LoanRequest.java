package com.p2plending.loan.domain.entity;

import com.p2plending.loan.domain.enums.LoanStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "loan_requests",
    indexes = {
        @Index(name = "idx_loan_borrower",  columnList = "borrowerId"),
        @Index(name = "idx_loan_status",    columnList = "status"),
        @Index(name = "idx_loan_created",   columnList = "createdAt")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long borrowerId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    /** Annual interest rate as a percentage, e.g. 12.50 means 12.5% p.a. */
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal interestRate;

    @Column(nullable = false)
    private Integer termMonths;

    @Column(nullable = false, length = 500)
    private String purpose;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private LoanStatus status = LoanStatus.PENDING;

    /** Running total of accepted offers — drives FUNDED transition. */
    @Column(nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal fundedAmount = BigDecimal.ZERO;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public BigDecimal getRemainingAmount() {
        return amount.subtract(fundedAmount);
    }

    public boolean isFullyFunded() {
        return fundedAmount.compareTo(amount) >= 0;
    }
}
