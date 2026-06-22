package com.p2plending.loan.dto.request;

import com.p2plending.loan.domain.entity.LoanFeeConfig;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class FeeConfigUpdateRequest {
    @NotNull
    private String feeType;

    private String feeName;

    @NotNull
    @DecimalMin("0")
    private BigDecimal feeAmount;

    private LoanFeeConfig.CalcType calcType;

    /** VAT rate — nếu null giữ nguyên giá trị cũ (mặc định 0.10). */
    private BigDecimal vatRate;

    private Boolean isActive;
}
