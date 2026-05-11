package com.p2plending.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class KycVerifyRequest {

    @NotBlank(message = "OTP không được để trống")
    @Size(min = 6, max = 6, message = "OTP phải gồm 6 chữ số")
    @Pattern(regexp = "^[0-9]{6}$", message = "OTP chỉ chứa chữ số")
    private String otp;
}
