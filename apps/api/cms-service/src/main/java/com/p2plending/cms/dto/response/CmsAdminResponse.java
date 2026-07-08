package com.p2plending.cms.dto.response;

import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CmsAdminResponse {
    private String id;
    private String username;
    private String email;
    private String fullName;
    /** Vai trò chính / nhãn hiển thị (tương thích client cũ). */
    private String role;
    /** Toàn bộ vai trò của tài khoản. */
    private List<String> roles;
}
