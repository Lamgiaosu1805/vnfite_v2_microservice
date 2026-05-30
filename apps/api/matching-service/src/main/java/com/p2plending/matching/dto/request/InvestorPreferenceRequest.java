package com.p2plending.matching.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class InvestorPreferenceRequest {

    @NotNull @DecimalMin("100.00")
    private BigDecimal minInvestmentAmount;

    @NotNull @DecimalMin("100.00")
    private BigDecimal maxInvestmentAmount;

    @NotNull @DecimalMin("0.01") @DecimalMax("100.00")
    private BigDecimal minInterestRate;

    @DecimalMin("0.01") @DecimalMax("100.00")
    private BigDecimal maxInterestRate;   // null = no upper bound

    @NotNull @Min(1) @Max(360)
    private Integer minTermMonths;

    @NotNull @Min(1) @Max(360)
    private Integer maxTermMonths;
}
