package com.p2plending.cms.dto.response;

import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CmsAdminResponse {
    private String id;
    private String username;
    private String email;
    private String fullName;
    private String role;
}
