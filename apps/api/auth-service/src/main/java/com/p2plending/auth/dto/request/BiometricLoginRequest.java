package com.p2plending.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BiometricLoginRequest {

    @NotBlank(message = "Phone is required")
    private String phone;

    @NotBlank(message = "Biometric token is required")
    private String biometricToken;

    /** UUID thiết bị — cùng logic single-device như password login */
    private String deviceKey;
}
