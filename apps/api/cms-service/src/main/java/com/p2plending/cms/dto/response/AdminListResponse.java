package com.p2plending.cms.dto.response;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor
public class AdminListResponse {
    private String id;
    private String username;
    private String email;
    private String fullName;
    private String role;
    private boolean active;
    private boolean mustChangePassword;
    private LocalDateTime createdAt;
}
