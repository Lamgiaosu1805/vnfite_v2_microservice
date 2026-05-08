package com.p2plending.loan.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class LoanOfferCreateRequest {

    @NotNull(message = "Offer amount is required")
    @DecimalMin(value = "100.00", message = "Minimum offer amount is 100")
    @Digits(integer = 13, fraction = 2)
    private BigDecimal amount;
}
