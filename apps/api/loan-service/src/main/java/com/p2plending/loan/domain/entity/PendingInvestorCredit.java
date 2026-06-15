package com.p2plending.loan.domain.entity;

import com.p2plending.loan.domain.enums.PendingCreditStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Một khoản cộng tiền hoàn trả cho nhà đầu tư bị lỗi khi trả nợ, chờ job đối soát thử lại.
 * Thử lại idempotent theo {@code referenceId} (chính là khóa cộng ví ban đầu) nên không cộng trùng
 * kể cả khi lần đầu thực ra đã thành công ở payment-service nhưng phản hồi lỗi.
 */
@Entity
@Table(
    name = "pending_investor_credit",
    uniqueConstraints = @UniqueConstraint(name = "uq_pending_credit_ref", columnNames = "referenceId"),
    indexes = {
        @Index(name = "idx_pending_credit_status", columnList = "status"),
        @Index(name = "idx_pending_credit_loan",   columnList = "loanId")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingInvestorCredit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, length = 36)
    private String loanId;

    @Column(length = 40)
    private String loanCode;

    @Column(nullable = false, length = 36)
    private String investorId;

    @Column(nullable = false, length = 36)
    private String offerId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    /** Khóa idempotent — trùng với reference cộng ví ban đầu (vd "REPAY-IN-...-{offerId}"). */
    @Column(nullable = false, length = 100)
    private String referenceId;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PendingCreditStatus status = PendingCreditStatus.PENDING;

    @Column(nullable = false)
    @Builder.Default
    private int attempts = 0;

    @Column(length = 500)
    private String lastError;

    @Column(nullable = false)
    @Builder.Default
    private boolean isDeleted = false;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
