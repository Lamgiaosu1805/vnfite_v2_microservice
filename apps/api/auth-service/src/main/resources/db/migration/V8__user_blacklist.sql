-- Blacklist khách hàng được đồng bộ từ hệ thống VNFITE cũ.
-- Khách blacklisted vẫn đăng nhập/xem thông tin được, nhưng không được đăng ký gọi vốn.

DROP PROCEDURE IF EXISTS add_user_blacklist_columns;

DELIMITER //
CREATE PROCEDURE add_user_blacklist_columns()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'blacklisted'
    ) THEN
        ALTER TABLE users ADD COLUMN blacklisted TINYINT(1) NOT NULL DEFAULT 0;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'blacklisted_at'
    ) THEN
        ALTER TABLE users ADD COLUMN blacklisted_at DATETIME NULL;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'blacklist_source'
    ) THEN
        ALTER TABLE users ADD COLUMN blacklist_source VARCHAR(50) NULL;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'blacklist_reason'
    ) THEN
        ALTER TABLE users ADD COLUMN blacklist_reason VARCHAR(255) NULL;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'users' AND index_name = 'idx_users_blacklisted'
    ) THEN
        CREATE INDEX idx_users_blacklisted ON users (blacklisted);
    END IF;
END//
DELIMITER ;

CALL add_user_blacklist_columns();

DROP PROCEDURE add_user_blacklist_columns;
