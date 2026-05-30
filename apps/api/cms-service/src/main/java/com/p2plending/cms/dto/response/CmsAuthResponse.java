package com.p2plending.cms.dto.response;

import lombok.Builder;
import lombok.Data;

@Data @Builder
public class CmsAuthResponse {
    private String accessToken;
    private long expiresIn;
    private CmsAdminResponse admin;
}
