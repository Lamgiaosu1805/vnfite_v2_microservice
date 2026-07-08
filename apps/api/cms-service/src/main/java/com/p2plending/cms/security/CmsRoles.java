package com.p2plending.cms.security;

import java.util.Set;

/**
 * Danh mục vai trò CMS. Ngoài SUPER_ADMIN (chủ hệ thống) và nhãn gộp ADMIN/OPS cũ,
 * bổ sung các vai trò theo phòng ban để phân quyền hẹp và cho phép một tài khoản
 * mang nhiều vai trò cùng lúc.
 */
public final class CmsRoles {

    private CmsRoles() {}

    // Vai trò hệ thống & nhãn gộp cũ (giữ để tương thích ngược)
    public static final String SUPER_ADMIN = "SUPER_ADMIN";
    public static final String ADMIN = "ADMIN";
    public static final String OPS = "OPS";

    // Vai trò theo phòng ban
    public static final String CUSTOMER_SUPPORT = "CUSTOMER_SUPPORT";
    public static final String APPRAISER = "APPRAISER";
    public static final String APPROVER = "APPROVER";
    public static final String FINANCE = "FINANCE";
    public static final String CONTENT = "CONTENT";
    public static final String HR = "HR";

    /** Các vai trò được phép gán cho tài khoản qua màn Quản lý Admin (không gồm SUPER_ADMIN). */
    public static final Set<String> ASSIGNABLE = Set.of(
            ADMIN, OPS, CUSTOMER_SUPPORT, APPRAISER, APPROVER, FINANCE, CONTENT, HR
    );

    public static boolean isValidAssignable(String role) {
        return role != null && ASSIGNABLE.contains(role);
    }
}
