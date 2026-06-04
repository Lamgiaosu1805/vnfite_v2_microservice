package com.p2plending.cms.dto.response;

import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CmsAuthResponse {
    private String accessToken;
    private long expiresIn;
    private CmsAdminResponse admin;
    /** true = frontend phải redirect sang trang đổi mật khẩu ngay */
    private boolean mustChangePassword;
}
