package com.p2plending.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "Phone number is required")
    @Pattern(
        regexp = "^(\\+84|84|0)[3|5|7|8|9][0-9]{8}$",
        message = "Invalid Vietnamese phone number"
    )
    private String phone;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).*$",
        message = "Password must contain at least one uppercase letter, one lowercase letter, one digit and one special character"
    )
    private String password;

    @Pattern(
        regexp = "^(\\+84|84|0)[3|5|7|8|9][0-9]{8}$",
        message = "Invalid Vietnamese phone number for referrer"
    )
    private String referrerPhone;
}
