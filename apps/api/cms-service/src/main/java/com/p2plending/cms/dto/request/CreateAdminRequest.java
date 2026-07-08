package com.p2plending.cms.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class CreateAdminRequest {
    @NotBlank
    private String fullName;

    @NotBlank
    @Email
    private String email;

    /** Danh sách vai trò gán cho tài khoản — kiểm tra hợp lệ ở tầng service. */
    @NotEmpty(message = "Phải chọn ít nhất một vai trò")
    private List<String> roles;
}
