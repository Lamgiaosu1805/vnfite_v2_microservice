package com.p2plending.cms.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Xác nhận thiết lập TOTP: gửi kèm secret + mã từ app để kích hoạt */
@Data
public class TotpConfirmRequest {
    @NotBlank
    @Size(min = 16, max = 64)
    private String secret;

    @NotBlank
    @Size(min = 6, max = 6, message = "Mã OTP phải có đúng 6 chữ số")
    private String code;
}
