package com.p2plending.loan.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class LoanCreateRequest {

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "1000.00", message = "Minimum loan amount is 1,000")
    @DecimalMax(value = "500000000.00", message = "Maximum loan amount is 500,000,000")
    @Digits(integer = 13, fraction = 2)
    private BigDecimal amount;

    @NotNull(message = "Term is required")
    @Min(value = 1,   message = "Minimum term is 1 month")
    @Max(value = 360, message = "Maximum term is 360 months (30 years)")
    private Integer termMonths;

    @NotBlank(message = "Purpose is required")
    @Size(min = 10, max = 500, message = "Purpose must be between 10 and 500 characters")
    private String purpose;

    @Size(max = 100)
    private String referredBy;

    @DecimalMin(value = "0", inclusive = false, message = "Monthly income must be positive")
    @Digits(integer = 13, fraction = 2)
    private BigDecimal monthlyIncome;

    @Size(max = 100)
    private String occupation;

    @Size(max = 500)
    private String currentAddress;
}
