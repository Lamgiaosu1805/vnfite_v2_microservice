package com.p2plending.cms.security;

/**
 * Principal đã xác thực trong CMS session.
 *
 * @param userId   ID của CmsAdminUser trong cms_db
 * @param username Tên đăng nhập
 * @param fullName Họ tên đầy đủ (từ JWT claim fullName)
 * @param email    Email admin
 * @param role     SUPER_ADMIN | ADMIN | OPS (lấy từ JWT claim roles[0], bỏ tiền tố ROLE_)
 */
public record CmsPrincipal(String userId, String username, String fullName, String email, String role) {

    /** Tên hiển thị: ưu tiên fullName, fallback về username nếu chưa có. */
    public String displayName() {
        return (fullName != null && !fullName.isBlank()) ? fullName : username;
    }

    /** Kiểm tra admin hiện tại có phải ban lãnh đạo (ADMIN hoặc SUPER_ADMIN) không. */
    public boolean isLeader() {
        return "ADMIN".equals(role) || "SUPER_ADMIN".equals(role);
    }
}
