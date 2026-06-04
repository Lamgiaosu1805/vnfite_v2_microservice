package com.p2plending.cms.dto.response;

import lombok.Builder;
import lombok.Data;

/** Trả về sau bước xác thực mật khẩu — frontend tiếp tục với TOTP */
@Data
@Builder
public class TotpPendingResponse {
    /** Token ngắn hạn (5 phút) dùng cho bước TOTP tiếp theo */
    private String pendingToken;
    /** false = cần thiết lập TOTP lần đầu, true = chỉ cần nhập mã */
    private boolean totpEnabled;
}
