package com.p2plending.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Bật đăng nhập sinh trắc học (bước verify).
 * Thiết bị đã tạo cặp khóa trong Secure Enclave / Keystore và gửi public key lên cùng OTP.
 */
@Data
public class BiometricEnableRequest {

    @NotBlank(message = "OTP không được để trống")
    @Size(min = 6, max = 6, message = "OTP phải gồm 6 chữ số")
    @Pattern(regexp = "^[0-9]{6}$", message = "OTP chỉ chứa chữ số")
    private String otp;

    /** Public key base64 (X.509 SPKI) do thiết bị sinh ra. */
    @NotBlank(message = "Public key không được để trống")
    private String publicKey;
}
