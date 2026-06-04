package com.p2plending.loan.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class InternalLoanReviewRequest {
    @DecimalMin(value = "0.01", message = "Interest rate must be at least 0.01%")
    @DecimalMax(value = "100.00", message = "Interest rate cannot exceed 100%")
    @Digits(integer = 3, fraction = 2)
    private BigDecimal interestRate;

    @Size(max = 500)
    private String reason;

    private String reviewedBy;
}
