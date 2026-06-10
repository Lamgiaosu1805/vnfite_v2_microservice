package com.p2plending.payment.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class WithdrawRequest {
    @NotNull
    @DecimalMin(value = "10000", message = "Số tiền rút tối thiểu 10.000 VND")
    private BigDecimal amount;

    /** ID của linked bank (từ GET /api/payment/banks) */
    @NotBlank
    private String linkedBankId;
}
