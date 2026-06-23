package com.p2plending.cms.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/** Trả về sau bước xác thực mật khẩu — frontend tiếp tục với TOTP */
@Data
@Builder
@NoArgsConstructor @AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TotpPendingResponse {
    /** Token ngắn hạn (5 phút) dùng cho bước TOTP tiếp theo */
    private String pendingToken;
    /** false = cần thiết lập TOTP lần đầu, true = chỉ cần nhập mã */
    private boolean totpEnabled;
    /** Chỉ có khi APP_TOTP_REQUIRED=false — frontend bỏ qua bước TOTP */
    private String accessToken;
    private CmsAdminResponse admin;
    private Boolean mustChangePassword;
}
