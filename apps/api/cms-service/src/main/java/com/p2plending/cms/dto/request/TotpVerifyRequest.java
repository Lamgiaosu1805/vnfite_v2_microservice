package com.p2plending.cms.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Xác thực TOTP khi đăng nhập (đã thiết lập 2FA trước đó) */
@Data
public class TotpVerifyRequest {
    @NotBlank
    @Size(min = 6, max = 6, message = "Mã OTP phải có đúng 6 chữ số")
    private String code;
}
