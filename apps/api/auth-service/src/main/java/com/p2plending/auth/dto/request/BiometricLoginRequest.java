package com.p2plending.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Bước 2 đăng nhập sinh trắc học: thiết bị gửi chữ ký của challenge.
 * Server verify chữ ký bằng public key đã lưu — không có shared secret nào truyền qua mạng.
 */
@Data
public class BiometricLoginRequest {

    @NotBlank(message = "Phone is required")
    private String phone;

    /** Chữ ký base64 của challenge, ký bằng private key trong Secure Enclave / Keystore (SHA256withRSA). */
    @NotBlank(message = "Signature is required")
    private String signature;

    /** UUID thiết bị — cùng logic single-device như password login */
    private String deviceKey;

    private String deviceName;
    private String platform;
}
