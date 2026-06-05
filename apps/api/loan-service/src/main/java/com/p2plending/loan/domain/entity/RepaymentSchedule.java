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

    public BigDecimal getRemainingDue() {
        return totalDue.subtract(paidAmount);
    }

    public boolean isSettled() {
        return status == RepaymentStatus.PAID;
    }
}
