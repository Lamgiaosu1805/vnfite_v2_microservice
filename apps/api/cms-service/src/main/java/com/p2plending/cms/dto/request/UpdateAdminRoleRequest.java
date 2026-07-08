package com.p2plending.cms.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class UpdateAdminRoleRequest {
    /** Danh sách vai trò mới — kiểm tra hợp lệ ở tầng service. */
    @NotEmpty(message = "Phải chọn ít nhất một vai trò")
    private List<String> roles;
}
