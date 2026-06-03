-- Dev seed data — chỉ load khi SPRING_PROFILES_ACTIVE=dev
-- Xóa toàn bộ data cũ trước khi seed lại

SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE refresh_tokens;
TRUNCATE TABLE kyc_submissions;
TRUNCATE TABLE users;
SET FOREIGN_KEY_CHECKS = 1;

-- Không seed user — người dùng tự đăng ký qua app
