-- Withdrawal requests: bảng chính theo dõi vòng đời yêu cầu rút tiền
CREATE TABLE withdrawal_requests (
    id                  VARCHAR(36)    NOT NULL PRIMARY KEY,
    user_id             VARCHAR(36)    NOT NULL,
    wallet_id           VARCHAR(36)    NOT NULL,
    linked_bank_id      VARCHAR(36)    NOT NULL,
    amount              DECIMAL(15,2)  NOT NULL,
    status              ENUM(
        'INITIATED',
        'OTP_PENDING',
        'FUNDS_LOCKED',
        'PENDING_APPROVAL',
        'APPROVED',
        'TRANSFER_INITIATED',
        'PROCESSING',
        'COMPLETED',
        'REJECTED',
        'TRANSFER_FAILED',
        'FAILED',
        'CANCELLED',
        'FUNDS_RELEASED'
    ) NOT NULL DEFAULT 'INITIATED',
    -- TIKLUY transactionId gửi đi (để match callback)
    transfer_ref        VARCHAR(100),
    -- FT number từ MB Bank (có trong callback thành công)
    mb_ft_number        VARCHAR(50),
    -- WalletTransaction id tạo khi FUNDS_LOCKED (để history)
    wallet_txn_id       VARCHAR(36),
    failure_reason      VARCHAR(500),
    reject_reason       VARCHAR(500),
    approved_by         VARCHAR(36),
    retry_count         INT            NOT NULL DEFAULT 0,
    max_retries         INT            NOT NULL DEFAULT 3,
    is_deleted          TINYINT(1)     NOT NULL DEFAULT 0,
    created_at          DATETIME       DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_wr_user_id   (user_id),
    INDEX idx_wr_status    (status),
    INDEX idx_wr_transfer_ref (transfer_ref)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Audit log: mỗi state transition tạo 1 bản ghi
CREATE TABLE withdrawal_audit_logs (
    id              VARCHAR(36)  NOT NULL PRIMARY KEY,
    withdrawal_id   VARCHAR(36)  NOT NULL,
    from_status     VARCHAR(30),
    to_status       VARCHAR(30)  NOT NULL,
    actor           VARCHAR(100) NOT NULL,
    actor_type      ENUM('USER','ADMIN','SYSTEM') NOT NULL,
    note            TEXT,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_wal_withdrawal_id (withdrawal_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Config chuyển tiền: tách hardcode ra khỏi code
CREATE TABLE withdrawal_transfer_configs (
    id                      INT            NOT NULL AUTO_INCREMENT PRIMARY KEY,
    source_label            VARCHAR(20)    NOT NULL DEFAULT 'VNFITE',
    -- Tài khoản debit công ty (trước là hardcode '6966638888' trong MBPaymentService)
    debit_account           VARCHAR(30)    NOT NULL,
    debit_name              VARCHAR(100)   NOT NULL,
    -- Template remark: {txId} {accNo} {accName} là placeholder
    remark_template         VARCHAR(200)   NOT NULL DEFAULT 'YF {txId} {accNo} {accName} RT',
    -- Ngưỡng tự động phê duyệt (< threshold = auto, >= threshold = chờ CMS)
    auto_approve_limit      DECIMAL(15,2)  NOT NULL DEFAULT 50000000,
    max_per_txn             DECIMAL(15,2)  NOT NULL DEFAULT 500000000,
    max_daily_total         DECIMAL(15,2)  NOT NULL DEFAULT 2500000000,
    max_daily_count         INT            NOT NULL DEFAULT 5,
    max_retries             INT            NOT NULL DEFAULT 3,
    active                  TINYINT(1)     NOT NULL DEFAULT 1,
    is_deleted              TINYINT(1)     NOT NULL DEFAULT 0,
    created_at              DATETIME       DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Seed config mặc định (idempotent)
INSERT INTO withdrawal_transfer_configs
    (source_label, debit_account, debit_name, remark_template,
     auto_approve_limit, max_per_txn, max_daily_total, max_daily_count, max_retries, active)
VALUES
    ('VNFITE', '6966638888', 'CONG TY CO PHAN CONG NGHE TAI CHINH',
     'YF {txId} {accNo} {accName} RT',
     50000000, 500000000, 2500000000, 5, 3, 1)
ON DUPLICATE KEY UPDATE updated_at = updated_at;
