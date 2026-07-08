package com.p2plending.cms.security;

import java.util.Set;

/**
 * Principal đã xác thực trong CMS session.
 *
 * @param userId   ID của CmsAdminUser trong cms_db
 * @param username Tên đăng nhập
 * @param fullName Họ tên đầy đủ (từ JWT claim fullName)
 * @param email    Email admin
 * @param role     Vai trò chính / nhãn hiển thị (roles[0] cũ, bỏ tiền tố ROLE_) — giữ để tương thích
 * @param roles    Toàn bộ vai trò (đã bỏ tiền tố ROLE_) — nguồn quyết định phân quyền
 */
public record CmsPrincipal(String userId, String username, String fullName, String email,
                           String role, Set<String> roles) {

    /** Tên hiển thị: ưu tiên fullName, fallback về username nếu chưa có. */
    public String displayName() {
        return (fullName != null && !fullName.isBlank()) ? fullName : username;
    }

    public boolean hasRole(String r) {
        return roles != null && roles.contains(r);
    }

    public boolean hasAnyRole(String... rs) {
        if (roles == null) return false;
        for (String r : rs) {
            if (roles.contains(r)) return true;
        }
        return false;
    }

    /** Ban lãnh đạo / có quyền phê duyệt (SUPER_ADMIN, nhãn gộp ADMIN cũ, hoặc APPROVER). */
    public boolean isLeader() {
        return hasAnyRole(CmsRoles.SUPER_ADMIN, CmsRoles.ADMIN, CmsRoles.APPROVER);
    }
}
