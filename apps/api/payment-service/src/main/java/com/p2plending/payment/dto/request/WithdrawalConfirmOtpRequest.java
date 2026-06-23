package com.p2plending.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class WithdrawalConfirmOtpRequest {

    @NotBlank(message = "Vui lòng nhập mã OTP.")
    @Pattern(regexp = "\\d{6}", message = "OTP phải là 6 chữ số.")
    private String otp;
}
