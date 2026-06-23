package com.p2plending.payment.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class WithdrawalInitiateRequest {

    @NotNull(message = "Vui lòng nhập số tiền rút.")
    @DecimalMin(value = "10000", message = "Số tiền rút tối thiểu 10.000 VND.")
    private BigDecimal amount;

    @NotBlank(message = "Vui lòng chọn tài khoản ngân hàng nhận tiền.")
    private String linkedBankId;
}
