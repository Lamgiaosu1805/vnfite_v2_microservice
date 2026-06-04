package com.p2plending.cms.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SetupRequest {
    @NotBlank
    @Size(min = 3, max = 60)
    private String username;

    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Size(max = 100)
    private String fullName;

    @NotBlank
    @Size(min = 8, max = 100)
    private String password;
}
