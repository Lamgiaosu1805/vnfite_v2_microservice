-- Lớp quyền-lẻ: cho phép cấp 1 tính năng nhạy cảm cụ thể cho tài khoản ngoài vai trò
-- phòng ban mặc định (vd: kế toán được cấp thêm quyền loan.approve mà không cần gán
-- cả vai trò APPROVER). Bảng rỗng mặc định — quyền lẻ là opt-in, không seed.
CREATE TABLE IF NOT EXISTS cms_admin_user_permissions (
    user_id    VARCHAR(36) NOT NULL,
    permission VARCHAR(50) NOT NULL,
    PRIMARY KEY (user_id, permission),
    CONSTRAINT fk_cms_admin_user_permissions_user
        FOREIGN KEY (user_id) REFERENCES cms_admin_users(id)
);
