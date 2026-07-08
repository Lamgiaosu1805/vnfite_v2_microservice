package com.p2plending.cms.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class UpdateAdminPermissionsRequest {
    /** Danh sách quyền lẻ mới — rỗng nghĩa là thu hồi hết quyền lẻ. Kiểm tra hợp lệ ở tầng service. */
    private List<String> permissions;
}
