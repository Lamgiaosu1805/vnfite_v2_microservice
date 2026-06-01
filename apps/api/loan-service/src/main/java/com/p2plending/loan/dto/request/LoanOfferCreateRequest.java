package com.p2plending.loan.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class LoanOfferCreateRequest {

    @NotNull(message = "Số tiền đầu tư là bắt buộc")
    @DecimalMin(value = "500000", message = "Số tiền đầu tư tối thiểu là 500,000 VNĐ")
    @Digits(integer = 13, fraction = 2)
    private BigDecimal amount;
}
