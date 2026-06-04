-- Chạy một lần khi MySQL container khởi động lần đầu
-- Chỉ tạo databases và cấp quyền
-- Tables được quản lý bởi Flyway trong từng service

SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS auth_db         CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS loan_db         CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS cms_db          CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS matching_db     CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS payment_db      CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS notification_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

GRANT ALL PRIVILEGES ON auth_db.*         TO 'p2p_user'@'%';
GRANT ALL PRIVILEGES ON loan_db.*         TO 'p2p_user'@'%';
GRANT ALL PRIVILEGES ON cms_db.*          TO 'p2p_user'@'%';
GRANT ALL PRIVILEGES ON matching_db.*     TO 'p2p_user'@'%';
GRANT ALL PRIVILEGES ON payment_db.*      TO 'p2p_user'@'%';
GRANT ALL PRIVILEGES ON notification_db.* TO 'p2p_user'@'%';
FLUSH PRIVILEGES;
