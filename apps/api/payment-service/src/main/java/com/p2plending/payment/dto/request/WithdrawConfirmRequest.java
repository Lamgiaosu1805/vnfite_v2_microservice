package com.p2plending.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WithdrawConfirmRequest {
    @NotBlank
    private String otp;
}
