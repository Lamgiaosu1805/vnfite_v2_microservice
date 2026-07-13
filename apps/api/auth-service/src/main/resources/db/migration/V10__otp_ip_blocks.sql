CREATE TABLE IF NOT EXISTS otp_ip_blocks (
    id CHAR(36) NOT NULL,
    ip_address VARCHAR(64) NOT NULL,
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    reason VARCHAR(255) NOT NULL,
    blocked_by VARCHAR(100) NOT NULL,
    unblocked_by VARCHAR(100) NULL,
    unblocked_at DATETIME NULL,
    is_deleted TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_otp_ip_blocks_ip_address (ip_address),
    KEY idx_otp_ip_blocks_active (is_active)
);

CREATE TABLE IF NOT EXISTS otp_ip_unblock_requests (
    id CHAR(36) NOT NULL,
    ip_address VARCHAR(64) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    requester_note VARCHAR(255) NULL,
    review_note VARCHAR(255) NULL,
    reviewed_by VARCHAR(100) NULL,
    reviewed_at DATETIME NULL,
    is_deleted TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_otp_ip_unblock_requests_status_created (status, created_at),
    KEY idx_otp_ip_unblock_requests_ip_phone (ip_address, phone)
);

INSERT INTO otp_ip_blocks (id, ip_address, is_active, reason, blocked_by)
VALUES
    ('6a5e70e1-6c0d-490e-9383-8c72d231bc0e', '103.228.37.72', 1, 'Tự động gửi OTP đăng ký với tần suất bất thường', 'SYSTEM_SECURITY'),
    ('2d899a21-0cfc-41e3-a0d1-52e0f9e5eb77', '23.142.84.215', 1, 'Tự động gửi OTP đăng ký với tần suất bất thường', 'SYSTEM_SECURITY')
ON DUPLICATE KEY UPDATE
    is_active = VALUES(is_active),
    reason = VALUES(reason),
    blocked_by = VALUES(blocked_by),
    is_deleted = 0;
