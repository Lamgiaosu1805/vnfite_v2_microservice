package com.p2plending.loan.domain.entity;

import com.p2plending.loan.domain.enums.RepaymentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    /** Tổng gốc+lãi đã trả = interest_paid + principal_paid (bất biến, giữ cho báo cáo cũ). */
    @Column(nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal paidAmount = BigDecimal.ZERO;

    /** Phần LÃI đã trả của kỳ (hạch toán Phí→Lãi→Gốc: lãi trả trước gốc). */
    @Column(name = "interest_paid", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal interestPaid = BigDecimal.ZERO;

    /** Phần GỐC đã trả của kỳ. */
    @Column(name = "principal_paid", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal principalPaid = BigDecimal.ZERO;

    /** Tổng phí phạt = interest_penalty + principal_penalty (bất biến, giữ cho báo cáo cũ). */
    @Column(name = "late_fee", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal lateFee = BigDecimal.ZERO;

    /** Tổng phí phạt đã trả = interest_penalty_paid + principal_penalty_paid (bất biến). */
    @Column(name = "late_fee_paid", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal lateFeePaid = BigDecimal.ZERO;

    /** Phí phạt LÃI quá hạn lũy kế (lãi chưa trả × 10%/năm × ngày/365). */
    @Column(name = "interest_penalty", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal interestPenalty = BigDecimal.ZERO;

    /** Phần phí phạt lãi đã trả. */
    @Column(name = "interest_penalty_paid", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal interestPenaltyPaid = BigDecimal.ZERO;

    /** Phí phạt GỐC quá hạn lũy kế (gốc chưa trả × (150%×lãi suất)/năm × ngày/365). */
    @Column(name = "principal_penalty", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal principalPenalty = BigDecimal.ZERO;

    /** Phần phí phạt gốc đã trả. */
    @Column(name = "principal_penalty_paid", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal principalPenaltyPaid = BigDecimal.ZERO;

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

    /** Lãi còn phải trả của kỳ. */
    public BigDecimal getInterestOutstanding() {
        return money(interestDue).subtract(money(interestPaid)).max(BigDecimal.ZERO);
    }

    /** Gốc còn phải trả của kỳ. */
    public BigDecimal getPrincipalOutstanding() {
        return money(principalDue).subtract(money(principalPaid)).max(BigDecimal.ZERO);
    }

    /** Gốc + lãi còn phải trả của kỳ (chưa gồm phí phạt). */
    public BigDecimal getRemainingDue() {
        return money(totalDue).subtract(money(paidAmount)).max(BigDecimal.ZERO);
    }

    /** Phí phạt lãi quá hạn còn phải trả. */
    public BigDecimal getInterestPenaltyOutstanding() {
        return money(interestPenalty).subtract(money(interestPenaltyPaid)).max(BigDecimal.ZERO);
    }

    /** Phí phạt gốc quá hạn còn phải trả. */
    public BigDecimal getPrincipalPenaltyOutstanding() {
        return money(principalPenalty).subtract(money(principalPenaltyPaid)).max(BigDecimal.ZERO);
    }

    /** Tổng phí phạt còn phải trả của kỳ (cả lãi + gốc quá hạn). */
    public BigDecimal getLateFeeOutstanding() {
        return money(getInterestPenaltyOutstanding().add(getPrincipalPenaltyOutstanding()));
    }

    /** Tổng còn phải trả của kỳ = phí phạt + lãi + gốc còn lại. */
    public BigDecimal getTotalOutstanding() {
        return money(getRemainingDue().add(getLateFeeOutstanding()));
    }

    public boolean isSettled() {
        return status == RepaymentStatus.PAID;
    }

    // ── Mutators giữ bất biến tổng (paidAmount, lateFee, lateFeePaid) ─────────────

    /** Cộng phần lãi vừa trả → đồng bộ paidAmount. */
    public void addInterestPaid(BigDecimal v) {
        this.interestPaid = money(interestPaid).add(money(v));
        this.paidAmount = money(interestPaid).add(money(principalPaid));
    }

    /** Cộng phần gốc vừa trả → đồng bộ paidAmount. */
    public void addPrincipalPaid(BigDecimal v) {
        this.principalPaid = money(principalPaid).add(money(v));
        this.paidAmount = money(interestPaid).add(money(principalPaid));
    }

    /** Cộng phí phạt lãi lũy kế → đồng bộ lateFee. */
    public void addInterestPenalty(BigDecimal v) {
        this.interestPenalty = money(interestPenalty).add(money(v));
        this.lateFee = money(interestPenalty).add(money(principalPenalty));
    }

    /** Cộng phí phạt gốc lũy kế → đồng bộ lateFee. */
    public void addPrincipalPenalty(BigDecimal v) {
        this.principalPenalty = money(principalPenalty).add(money(v));
        this.lateFee = money(interestPenalty).add(money(principalPenalty));
    }

    /** Cộng phần phí phạt lãi vừa trả → đồng bộ lateFeePaid. */
    public void addInterestPenaltyPaid(BigDecimal v) {
        this.interestPenaltyPaid = money(interestPenaltyPaid).add(money(v));
        this.lateFeePaid = money(interestPenaltyPaid).add(money(principalPenaltyPaid));
    }

    /** Cộng phần phí phạt gốc vừa trả → đồng bộ lateFeePaid. */
    public void addPrincipalPenaltyPaid(BigDecimal v) {
        this.principalPenaltyPaid = money(principalPenaltyPaid).add(money(v));
        this.lateFeePaid = money(interestPenaltyPaid).add(money(principalPenaltyPaid));
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(0, RoundingMode.HALF_UP);
    }
}
