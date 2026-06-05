package com.p2plending.cms.security;

/**
 * Principal đã xác thực trong CMS session.
 *
 * @param userId   ID của CmsAdminUser trong cms_db
 * @param username Tên đăng nhập
 * @param email    Email admin
 * @param role     SUPER_ADMIN | ADMIN | OPS (lấy từ JWT claim roles[0], bỏ tiền tố ROLE_)
 */
public record CmsPrincipal(String userId, String username, String email, String role) {

    /** Kiểm tra admin hiện tại có phải ban lãnh đạo (ADMIN hoặc SUPER_ADMIN) không. */
    public boolean isLeader() {
        return "ADMIN".equals(role) || "SUPER_ADMIN".equals(role);
    }
}
