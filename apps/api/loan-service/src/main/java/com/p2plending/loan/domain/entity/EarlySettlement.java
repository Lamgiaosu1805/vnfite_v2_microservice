package com.p2plending.loan.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Sổ tất toán trước hạn — ghi nhận một lần khi người gọi vốn tất toán sớm một khoản.
 *
 * <p>Phí tất toán 5% (settlement_fee) là doanh thu VNFITE: borrower bị trừ đủ tổng payoff,
 * nhà đầu tư chỉ nhận gốc + lãi tới ngày tất toán + phí phạt quá hạn (sau thuế), phần 5% còn lại
 * đọng tại tài khoản tổng VNFITE. Bảng này KHÔNG di chuyển tiền — chỉ hạch toán để đối soát.
 * Mỗi khoản chỉ một dòng (unique loanId → idempotent).
 */
@Entity
@Table(
    name = "early_settlement",
    uniqueConstraints = @UniqueConstraint(name = "uq_early_settlement_loan", columnNames = "loanId"),
    indexes = {
        @Index(name = "idx_early_settlement_settled",  columnList = "settledAt"),
        @Index(name = "idx_early_settlement_borrower", columnList = "borrowerId")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EarlySettlement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, length = 36)
    private String loanId;

    @Column(length = 50)
    private String loanCode;

    @Column(length = 36)
    private String borrowerId;

    /** Gốc còn lại đã tất toán. */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal principalSettled;

    /** Lãi tính tới ngày tất toán (đã pro-rate kỳ đang chạy, miễn lãi kỳ tương lai). */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal interestToDate;

    /** Phí phạt quá hạn còn lại đã trả khi tất toán. */
    @Column(nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal penaltyPaid = BigDecimal.ZERO;

    /** Phí tất toán trước hạn = settlement_fee_rate% × gốc còn lại → VNFITE. */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal settlementFee;

    /** Tỷ lệ phí tất toán (%) tại thời điểm tất toán. */
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal settlementFeeRate;

    /** Tổng borrower phải trả = gốc + lãi tới ngày + phí phạt + phí tất toán. */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal totalPaid;

    @Column(nullable = false)
    private LocalDateTime settledAt;

    @Column(length = 100)
    private String settledBy;

    @Column(nullable = false)
    @Builder.Default
    private boolean isDeleted = false;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
