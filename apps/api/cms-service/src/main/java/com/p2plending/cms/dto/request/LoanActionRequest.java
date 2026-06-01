package com.p2plending.cms.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class LoanActionRequest {

    /** Required when approving — CMS sets the interest rate. */
    @DecimalMin(value = "0.01", message = "Interest rate must be at least 0.01%")
    @DecimalMax(value = "100.00", message = "Interest rate cannot exceed 100%")
    @Digits(integer = 3, fraction = 2)
    private BigDecimal interestRate;

    /** Required when rejecting. */
    @Size(max = 500)
    private String reason;
}
