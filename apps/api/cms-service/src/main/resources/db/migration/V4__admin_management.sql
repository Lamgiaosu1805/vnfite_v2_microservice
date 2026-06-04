-- Thêm cột quản lý admin
ALTER TABLE cms_admin_users
  ADD COLUMN must_change_password TINYINT(1) NOT NULL DEFAULT 0 AFTER password,
  ADD COLUMN created_by VARCHAR(36) NULL AFTER must_change_password;
