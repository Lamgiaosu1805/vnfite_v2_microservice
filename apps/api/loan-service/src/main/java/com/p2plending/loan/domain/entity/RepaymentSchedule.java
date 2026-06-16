package com.p2plending.loan.domain.entity;

import com.p2plending.loan.domain.enums.RepaymentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Một kỳ trả nợ trong lịch của một khoản vay. Sinh ra khi loan FUNDED. */
@Entity
@Table(
    name = "repayment_schedule",
    uniqueConstraints = @UniqueConstraint(name = "uq_schedule_loan_period", columnNames = {"loanId", "periodNumber"}),
    indexes = {
        @Index(name = "idx_schedule_loan",   columnList = "loanId"),
        @Index(name = "idx_schedule_due",    columnList = "dueDate"),
        @Index(name = "idx_schedule_status", columnList = "status")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepaymentSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Version
    private Long version;

    @Column(nullable = false, length = 36)
    private String loanId;

    @Column(nullable = false)
    private Integer periodNumber;

    @Column(nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal principalDue;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal interestDue;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal totalDue;

    @Column(nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal paidAmount = BigDecimal.ZERO;

    /** Phí phạt trả chậm đã tính tới hiện tại (job DPD cập nhật theo dpd). */
    @Column(name = "late_fee", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal lateFee = BigDecimal.ZERO;

    /** Phần phí phạt người gọi vốn đã trả. */
    @Column(name = "late_fee_paid", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal lateFeePaid = BigDecimal.ZERO;

    private LocalDateTime paidAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private RepaymentStatus status = RepaymentStatus.PENDING;

    /** Số ngày quá hạn của riêng kỳ này — job DPD cập nhật hàng ngày. */
    @Column(nullable = false)
    @Builder.Default
    private Integer dpd = 0;

    @Column(nullable = false)
    @Builder.Default
    private boolean isDeleted = false;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    /** Gốc + lãi còn phải trả của kỳ (chưa gồm phí phạt). */
    public BigDecimal getRemainingDue() {
        return totalDue.subtract(paidAmount);
    }

    /** Phí phạt còn phải trả của kỳ. */
    public BigDecimal getLateFeeOutstanding() {
        BigDecimal fee = lateFee != null ? lateFee : BigDecimal.ZERO;
        BigDecimal paid = lateFeePaid != null ? lateFeePaid : BigDecimal.ZERO;
        return fee.subtract(paid).max(BigDecimal.ZERO);
    }

    /** Tổng còn phải trả của kỳ = gốc + lãi + phí phạt còn lại. */
    public BigDecimal getTotalOutstanding() {
        return getRemainingDue().add(getLateFeeOutstanding());
    }

    public boolean isSettled() {
        return status == RepaymentStatus.PAID;
    }
}
