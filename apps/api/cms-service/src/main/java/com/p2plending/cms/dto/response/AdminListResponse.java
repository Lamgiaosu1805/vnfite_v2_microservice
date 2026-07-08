package com.p2plending.cms.dto.response;

import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AdminListResponse {
    private String id;
    private String username;
    private String email;
    private String fullName;
    /** Vai trò chính / nhãn hiển thị. */
    private String role;
    /** Toàn bộ vai trò của tài khoản. */
    private List<String> roles;
    private boolean active;
    private boolean mustChangePassword;
    private boolean totpEnabled;
    private LocalDateTime createdAt;
}
