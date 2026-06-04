package com.p2plending.cms.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class CreateAdminRequest {
    @NotBlank
    private String fullName;

    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Pattern(regexp = "ADMIN|OPS", message = "Role phải là ADMIN hoặc OPS")
    private String role;
}
