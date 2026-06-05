-- Lịch sử thiết bị đã từng đăng nhập tài khoản
-- Mỗi lần đăng nhập (password hoặc biometric) ghi 1 record
CREATE TABLE IF NOT EXISTS device_login_history (
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    user_id     VARCHAR(36)     NOT NULL,
    device_key  VARCHAR(100)    NOT NULL COMMENT 'UUID thiết bị',
    device_name VARCHAR(255)    NOT NULL COMMENT 'Tên thiết bị (vd: iPhone iOS 17.2)',
    platform    VARCHAR(20)     NOT NULL COMMENT 'ios | android | unknown',
    login_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted  TINYINT(1)      NOT NULL DEFAULT 0,
    created_at  DATETIME        DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    INDEX idx_dlh_user_id   (user_id),
    INDEX idx_dlh_device_key(device_key),
    INDEX idx_dlh_login_at  (login_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
