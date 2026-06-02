package com.p2plending.auth.dto.request;

import com.p2plending.auth.validation.VietnamesePhone;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ForgotPasswordOtpVerifyRequest {

    @NotBlank(message = "Phone number is required")
    @VietnamesePhone
    private String phone;

    @NotBlank(message = "OTP is required")
    @Pattern(regexp = "^[0-9]{6}$", message = "OTP must be 6 digits")
    private String otp;
}
