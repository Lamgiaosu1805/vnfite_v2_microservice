package com.p2plending.loan.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Sổ cái doanh thu phí — ghi nhận phí thẩm định + VAT VNFITE thu được mỗi lần giải ngân một khoản.
 *
 * <p>Tiền phí thực tế đọng trong tài khoản tổng (master/settlement) của VNFITE tại MB (khi giải ngân,
 * nhà đầu tư bị trừ đủ còn người gọi vốn chỉ nhận phần net sau phí). Bảng này KHÔNG di chuyển tiền —
 * chỉ hạch toán để đối soát/báo cáo doanh thu phí. Mỗi khoản chỉ ghi một dòng (unique loanId → idempotent
 * khi retry giải ngân).
 */
@Entity
@Table(
    name = "fee_revenue_ledger",
    uniqueConstraints = @UniqueConstraint(name = "uq_fee_revenue_loan", columnNames = "loanId"),
    indexes = {
        @Index(name = "idx_fee_revenue_disbursed", columnList = "disbursedAt"),
        @Index(name = "idx_fee_revenue_borrower",  columnList = "borrowerId")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeeRevenueLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, length = 36)
    private String loanId;

    @Column(length = 50)
    private String loanCode;

    @Column(length = 36)
    private String borrowerId;

    /** Số tiền khoản đã giải ngân (gốc) — để đối chiếu tỷ lệ phí. */
    @Column(nullable = false, precision = 15, scale = 0)
    private BigDecimal loanAmount;

    /** Tỷ lệ phí thẩm định (%) tại thời điểm trình duyệt. */
    @Column(precision = 5, scale = 2)
    private BigDecimal appraisalFeeRate;

    /** Phí thẩm định (chưa gồm VAT). */
    @Column(nullable = false, precision = 15, scale = 0)
    private BigDecimal appraisalFee;

    /** VAT 10% trên phí thẩm định. */
    @Column(nullable = false, precision = 15, scale = 0)
    private BigDecimal vatAmount;

    /** Tổng phí thu được = phí thẩm định + VAT. */
    @Column(nullable = false, precision = 15, scale = 0)
    private BigDecimal totalFee;

    /** Thời điểm giải ngân — mốc ghi nhận doanh thu. */
    @Column(nullable = false)
    private LocalDateTime disbursedAt;

    /** Người thực hiện giải ngân (OPS/ADMIN). */
    @Column(length = 100)
    private String disbursedBy;

    @Column(nullable = false)
    @Builder.Default
    private boolean isDeleted = false;

    /** Đảo ghi nhận khi hoàn giải ngân trước khi có giao dịch phân phối. */
    @Column(nullable = false)
    @Builder.Default
    private boolean isReversed = false;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
