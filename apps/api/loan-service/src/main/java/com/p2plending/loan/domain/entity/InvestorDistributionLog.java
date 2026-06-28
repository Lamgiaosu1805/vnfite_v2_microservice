package com.p2plending.loan.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Ghi lại chi tiết phân bổ tiền cho từng nhà đầu tư trong mỗi lần trả nợ:
 * số tiền gốc, lãi, phí phạt, thuế TNCN 5% và số tiền thực nhận.
 */
@Entity
@Table(
    name = "investor_distribution_log",
    indexes = {
        @Index(name = "idx_inv_dist_loan",        columnList = "loanId"),
        @Index(name = "idx_inv_dist_investor",     columnList = "investorId"),
        @Index(name = "idx_inv_dist_txn",          columnList = "repaymentTransactionId"),
        @Index(name = "idx_inv_dist_distributed",  columnList = "distributedAt")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvestorDistributionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, length = 36)
    private String repaymentTransactionId;

    @Column(nullable = false, length = 36)
    private String loanId;

    @Column(length = 50)
    private String loanCode;

    @Column(length = 36)
    private String scheduleId;

    @Column(nullable = false, length = 36)
    private String offerId;

    @Column(nullable = false, length = 36)
    private String investorId;

    /** Tổng phần được phân bổ trước thuế (principalAmount + interestAmount + lateFeeAmount). */
    @Column(nullable = false, precision = 15, scale = 0)
    private BigDecimal grossAmount;

    /** Phần gốc — không chịu thuế TNCN. */
    @Column(nullable = false, precision = 15, scale = 0)
    private BigDecimal principalAmount;

    /** Phần lãi — chịu thuế TNCN. */
    @Column(nullable = false, precision = 15, scale = 0)
    private BigDecimal interestAmount;

    /** Phần phí phạt trả chậm — chịu thuế TNCN. */
    @Column(nullable = false, precision = 15, scale = 0)
    private BigDecimal lateFeeAmount;

    /** Tỷ lệ thuế TNCN áp dụng (vd: 0.0500 = 5%). */
    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal taxRate;

    /** Số tiền thuế TNCN khấu trừ tại nguồn (VNFITE giữ lại nộp thay nhà đầu tư). */
    @Column(nullable = false, precision = 15, scale = 0)
    private BigDecimal taxAmount;

    /** Số tiền thực cộng vào ví nhà đầu tư (grossAmount - taxAmount). */
    @Column(nullable = false, precision = 15, scale = 0)
    private BigDecimal netAmount;

    @Column(length = 200)
    private String creditRef;

    @Column(nullable = false)
    private LocalDateTime distributedAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean isDeleted = false;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
