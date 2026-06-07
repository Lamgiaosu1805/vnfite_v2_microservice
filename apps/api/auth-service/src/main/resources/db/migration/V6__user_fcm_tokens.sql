-- FCM push token của thiết bị đang active của user.
-- 1 user = 1 thiết bị active → user_id là PK (UPSERT khi token thay đổi).
CREATE TABLE IF NOT EXISTS user_fcm_tokens (
    user_id     VARCHAR(36)  NOT NULL COMMENT 'FK → users.id',
    fcm_token   TEXT         NOT NULL COMMENT 'Firebase Cloud Messaging token',
    device_key  VARCHAR(100) NULL     COMMENT 'UUID thiết bị gắn token này',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                             ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
