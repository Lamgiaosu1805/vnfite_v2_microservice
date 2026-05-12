package com.p2plending.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ForgotPasswordRequest {

    @NotBlank(message = "Phone number is required")
    @Pattern(
        regexp = "^(\\+84|84|0)[3|5|7|8|9][0-9]{8}$",
        message = "Invalid Vietnamese phone number"
    )
    private String phone;

    @Pattern(
        regexp = "^[0-9]{12}$",
        message = "CCCD must be 12 digits"
    )
    private String cccdNumber;
}
