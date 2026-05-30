package com.p2plending.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ChangePasswordVerifyRequest {

    @NotBlank(message = "OTP không được để trống")
    @Pattern(regexp = "^[0-9]{6}$", message = "OTP phải gồm 6 chữ số")
    private String otp;
}
