-- Dev seed data — chỉ load khi SPRING_PROFILES_ACTIVE=dev
-- Xóa toàn bộ data cũ trước khi seed lại

SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE daily_stats;
TRUNCATE TABLE cms_loans;
TRUNCATE TABLE cms_users;
TRUNCATE TABLE cms_admin_users;
SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================
-- CMS ADMIN USERS — tài khoản đăng nhập CMS portal
-- username: admin / password: Admin@1234
-- username: ops   / password: Admin@1234
-- ============================================================
INSERT INTO cms_admin_users
  (id, username, email, full_name, password, role, active, is_deleted, created_at, updated_at)
VALUES
  ('13338e69-6a36-4018-975c-46f9be5b3898',
   'admin', 'admin@vnfite.com', 'Super Admin',
   '$2y$10$R3xlpvd8fU6nWKPv7tjqEuAxsRt0Bxm5zMxNSGmy2tyrtRHidCYAW',
   'ADMIN', 1, 0, NOW(), NOW()),

  ('74c5aded-2189-47d0-8a7c-3afeed2f542c',
   'ops', 'ops@vnfite.com', 'Operations Team',
   '$2y$10$R3xlpvd8fU6nWKPv7tjqEuAxsRt0Bxm5zMxNSGmy2tyrtRHidCYAW',
   'OPS', 1, 0, NOW(), NOW());

-- Không seed cms_users / cms_loans / daily_stats — tự sinh qua Kafka khi user đăng ký
