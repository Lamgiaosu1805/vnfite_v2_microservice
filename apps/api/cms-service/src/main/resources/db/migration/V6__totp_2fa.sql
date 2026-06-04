-- Thêm cột hỗ trợ TOTP 2FA cho tài khoản CMS admin
ALTER TABLE cms_admin_users
  ADD COLUMN totp_secret  VARCHAR(64)  NULL    AFTER created_by,
  ADD COLUMN totp_enabled TINYINT(1)   NOT NULL DEFAULT 0 AFTER totp_secret;
