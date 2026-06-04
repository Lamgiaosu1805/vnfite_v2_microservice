-- Dev seed data — chỉ load khi SPRING_PROFILES_ACTIVE=dev
-- Reset data cấu hình (trừ cms_admin_users — tự tạo qua /cms/auth/setup)

SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE daily_stats;
TRUNCATE TABLE cms_loans;
TRUNCATE TABLE cms_users;
-- cms_admin_users KHÔNG truncate — admin tự tạo qua setup endpoint
SET FOREIGN_KEY_CHECKS = 1;
