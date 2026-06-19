package com.p2plending.auth.dto.request;

import com.p2plending.auth.validation.VietnamesePhone;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "Phone number is required")
    @VietnamesePhone
    private String phone;

    @NotBlank(message = "Password is required")
    @Size(min = 32, max = 100, message = "Password is required")
    private String password;

    @VietnamesePhone(message = "Invalid Vietnamese phone number for referrer")
    private String referrerPhone;
}
