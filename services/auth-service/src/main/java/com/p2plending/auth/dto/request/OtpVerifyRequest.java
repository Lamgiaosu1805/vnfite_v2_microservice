package com.p2plending.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OtpVerifyRequest {

    @NotBlank(message = "Phone number is required")
    @Pattern(
        regexp = "^(\\+84|84|0)[3|5|7|8|9][0-9]{8}$",
        message = "Invalid Vietnamese phone number"
    )
    private String phone;

    @NotBlank(message = "OTP is required")
    @Size(min = 6, max = 6, message = "OTP must be 6 digits")
    @Pattern(regexp = "^[0-9]{6}$", message = "OTP must contain digits only")
    private String otp;
}
