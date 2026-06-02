package com.p2plending.auth.dto.request;

import com.p2plending.auth.validation.VietnamesePhone;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ForgotPasswordCheckRequest {

    @NotBlank(message = "Phone number is required")
    @VietnamesePhone
    private String phone;
}
