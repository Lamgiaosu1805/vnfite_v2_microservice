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
}
