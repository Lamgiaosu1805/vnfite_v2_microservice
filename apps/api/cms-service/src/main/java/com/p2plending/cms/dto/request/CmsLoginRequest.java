package com.p2plending.cms.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CmsLoginRequest {
    @NotBlank
    private String username;

    @NotBlank
    private String password;
}
