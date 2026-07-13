package com.p2plending.auth.dto.request;

import lombok.Data;

/** Request body cho internal endpoint POST /internal/users/{userId}/business-profile/decision. */
@Data
public class BusinessProfileDecisionInternalRequest {

    private boolean approved;

    /** Bắt buộc khi từ chối — hiển thị cho người dùng trên app. */
    private String reason;

    /** Admin CMS thực hiện quyết định (displayName). */
    private String reviewedBy;

    /**
     * Tên công ty tra được từ VietQR (MST) tại thời điểm duyệt — CMS tự tra trước khi gửi quyết định.
     * Khi duyệt, nếu có giá trị này thì dùng làm tên chính thức thay cho tên tự nhập;
     * null/rỗng (VietQR không tra ra) thì giữ nguyên tên hồ sơ đã nộp.
     */
    private String resolvedBusinessName;
}
