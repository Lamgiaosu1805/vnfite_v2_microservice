-- Đa vai trò cho tài khoản CMS: một admin có thể mang nhiều vai trò phòng ban.
-- Cột `role` cũ trên cms_admin_users được giữ lại làm "vai trò chính / nhãn hiển thị"
-- và vẫn nằm trong JWT để các @PreAuthorize cũ (ADMIN/OPS/SUPER_ADMIN) tương thích ngược.

CREATE TABLE IF NOT EXISTS cms_admin_user_roles (
    user_id VARCHAR(36) NOT NULL,
    role    VARCHAR(30) NOT NULL,
    PRIMARY KEY (user_id, role),
    CONSTRAINT fk_cms_admin_user_roles_user
        FOREIGN KEY (user_id) REFERENCES cms_admin_users(id)
);

-- Seed vai trò chi tiết từ role hiện tại (idempotent — INSERT IGNORE, không phá dữ liệu).

-- SUPER_ADMIN giữ nguyên
INSERT IGNORE INTO cms_admin_user_roles (user_id, role)
SELECT id, 'SUPER_ADMIN'
FROM cms_admin_users
WHERE cms_admin_users.role = 'SUPER_ADMIN' AND is_deleted = 0;

-- OPS (chỉ đọc / giám sát) giữ nguyên
INSERT IGNORE INTO cms_admin_user_roles (user_id, role)
SELECT id, 'OPS'
FROM cms_admin_users
WHERE cms_admin_users.role = 'OPS' AND is_deleted = 0;

-- ADMIN cũ → đủ 6 vai trò nghiệp vụ để không mất quyền khi deploy.
-- Ban quản trị thu hẹp lại từng người sau ở màn Quản lý Admin.
INSERT IGNORE INTO cms_admin_user_roles (user_id, role)
SELECT u.id, r.role
FROM cms_admin_users u
CROSS JOIN (
    SELECT 'CUSTOMER_SUPPORT' AS role
    UNION ALL SELECT 'APPRAISER'
    UNION ALL SELECT 'APPROVER'
    UNION ALL SELECT 'FINANCE'
    UNION ALL SELECT 'CONTENT'
    UNION ALL SELECT 'HR'
) r
WHERE u.role = 'ADMIN' AND u.is_deleted = 0;
