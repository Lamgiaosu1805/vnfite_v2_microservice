package com.p2plending.cms.dto.response;

import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CreateAdminResponse {
    private String id;
    private String username;
    private String email;
    private String fullName;
    private String role;
    private List<String> roles;
    /** Quyền lẻ cấp thêm ngoài vai trò. */
    private List<String> permissions;
    /** Mật khẩu tự sinh — chỉ trả về 1 lần duy nhất khi tạo */
    private String generatedPassword;
}
