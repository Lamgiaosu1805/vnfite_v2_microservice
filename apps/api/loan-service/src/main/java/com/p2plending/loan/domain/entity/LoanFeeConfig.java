package com.p2plending.loan.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "loan_fee_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanFeeConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "fee_type", nullable = false, unique = true, length = 50)
    private String feeType;

    @Column(name = "fee_name", nullable = false, length = 100)
    private String feeName;

    @Column(name = "fee_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal feeAmount;

    @Column(name = "calc_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private CalcType calcType;

    @Column(name = "vat_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal vatRate;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum CalcType { FIXED, PERCENTAGE }

    /** Tính phí cho một khoản vay cụ thể. */
    public BigDecimal calculateFee(BigDecimal loanAmount) {
        if (!isActive || feeAmount.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        if (calcType == CalcType.PERCENTAGE) {
            return loanAmount.multiply(feeAmount).divide(new BigDecimal("100"));
        }
        return feeAmount;
    }

    /** VAT trên phí đã tính. */
    public BigDecimal calculateVat(BigDecimal fee) {
        return fee.multiply(vatRate);
    }
}
