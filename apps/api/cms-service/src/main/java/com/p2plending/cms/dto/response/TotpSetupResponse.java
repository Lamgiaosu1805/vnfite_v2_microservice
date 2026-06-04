package com.p2plending.cms.dto.response;

import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/** Thông tin để thiết lập TOTP lần đầu */
@Data
@Builder
@NoArgsConstructor @AllArgsConstructor
public class TotpSetupResponse {
    /** Base32 secret — để nhập thủ công vào app nếu không scan QR được */
    private String secret;
    /** otpauth:// URI — frontend render thành QR code */
    private String otpAuthUrl;
}
