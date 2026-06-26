package com.p2plending.cms.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UpdateAdminRoleRequest {
    @NotBlank
    @Pattern(regexp = "ADMIN|OPS", message = "Role phải là ADMIN hoặc OPS")
    private String role;
}
