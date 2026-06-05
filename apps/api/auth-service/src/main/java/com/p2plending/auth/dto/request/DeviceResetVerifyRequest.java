package com.p2plending.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DeviceResetVerifyRequest {

    @NotBlank(message = "Phone is required")
    private String phone;

    @NotBlank(message = "OTP is required")
    private String otp;
}
