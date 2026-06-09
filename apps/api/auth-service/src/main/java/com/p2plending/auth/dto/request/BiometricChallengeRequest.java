package com.p2plending.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Bước 1 đăng nhập sinh trắc học: thiết bị xin một challenge (nonce) để ký.
 */
@Data
public class BiometricChallengeRequest {

    @NotBlank(message = "Phone is required")
    private String phone;
}
