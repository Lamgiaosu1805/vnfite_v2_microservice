package com.p2plending.cms.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResetAdminPasswordResponse {
    private String id;
    private String username;
    private String fullName;
    private String generatedPassword;
}
