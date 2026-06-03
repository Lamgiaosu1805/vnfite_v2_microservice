-- Dev seed data — chỉ load khi SPRING_PROFILES_ACTIVE=dev
-- Xóa toàn bộ data cũ trước khi seed lại

SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE match_records;
TRUNCATE TABLE pending_loans;
TRUNCATE TABLE investor_preferences;
SET FOREIGN_KEY_CHECKS = 1;

-- Không seed — tất cả data sinh ra từ hoạt động thực của user
