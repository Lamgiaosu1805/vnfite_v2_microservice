package com.p2plending.loan.domain.entity;

import com.p2plending.loan.domain.enums.PaymentChannel;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Một lần ghi nhận tiền trả nợ về (đối tác thu hộ hoặc admin nhập tay). */
@Entity
@Table(
    name = "repayment_transaction",
    indexes = {
        @Index(name = "idx_repay_txn_loan",     columnList = "loanId"),
        @Index(name = "idx_repay_txn_schedule",  columnList = "scheduleId"),
        @Index(name = "idx_repay_txn_paid_at",   columnList = "paidAt")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, length = 36)
    private String loanId;

    /** Kỳ trả nợ mà giao dịch này áp vào (null nếu trả gộp nhiều kỳ). */
    @Column(length = 36)
    private String scheduleId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDateTime paidAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private PaymentChannel channel = PaymentChannel.MANUAL_ADMIN;

    /** Mã giao dịch từ đối tác thu hộ. */
    @Column(length = 100)
    private String externalRef;

    /** Admin id nếu nhập tay. */
    @Column(length = 36)
    private String recordedBy;

    @Column(length = 500)
    private String note;

    @Column(nullable = false)
    @Builder.Default
    private boolean isDeleted = false;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
