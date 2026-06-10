-- ─── wallets ────────────────────────────────────────────────────────────────
-- Một user có đúng một wallet (tạo khi KYC được duyệt)
CREATE TABLE IF NOT EXISTS wallets (
    id              VARCHAR(36)    NOT NULL PRIMARY KEY,
    user_id         VARCHAR(36)    NOT NULL UNIQUE COMMENT 'FK → auth_db.users.id',
    vnf_account_no  VARCHAR(20)    NOT NULL UNIQUE COMMENT 'Số TK định danh MB, vd VNF0000000001',
    total_balance   DECIMAL(15,2)  NOT NULL DEFAULT 0.00 COMMENT 'Số dư khả dụng (VND)',
    locked_balance  DECIMAL(15,2)  NOT NULL DEFAULT 0.00 COMMENT 'Số dư đang bị khóa (đang rút hoặc đang đầu tư)',
    is_deleted      TINYINT(1)     NOT NULL DEFAULT 0,
    created_at      DATETIME       DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── wallet_transactions ─────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS wallet_transactions (
    id              VARCHAR(36)    NOT NULL PRIMARY KEY,
    wallet_id       VARCHAR(36)    NOT NULL,
    type            VARCHAR(20)    NOT NULL COMMENT 'DEPOSIT | WITHDRAW | INVEST | INVEST_REFUND | REPAYMENT',
    amount          DECIMAL(15,2)  NOT NULL,
    status          VARCHAR(20)    NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING | SUCCESS | FAILED',
    -- Tham chiếu từ MB Bank (referenceNumber từ webhook nạp tiền)
    reference_id    VARCHAR(100)   NULL UNIQUE,
    -- Tham chiếu nội bộ (transactionId gửi sang TIKLUY khi rút tiền)
    external_ref    VARCHAR(100)   NULL,
    description     VARCHAR(500)   NULL,
    -- Snapshot số dư sau giao dịch
    balance_after   DECIMAL(15,2)  NULL,
    is_deleted      TINYINT(1)     NOT NULL DEFAULT 0,
    created_at      DATETIME       DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_wallet_id (wallet_id),
    INDEX idx_reference_id (reference_id),
    INDEX idx_external_ref (external_ref),
    CONSTRAINT fk_wt_wallet FOREIGN KEY (wallet_id) REFERENCES wallets (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── linked_banks ─────────────────────────────────────────────────────────────
-- Tài khoản ngân hàng thực của user để rút tiền ra
CREATE TABLE IF NOT EXISTS linked_banks (
    id              VARCHAR(36)    NOT NULL PRIMARY KEY,
    user_id         VARCHAR(36)    NOT NULL,
    bank_code       VARCHAR(20)    NOT NULL COMMENT 'Mã ngân hàng MB dùng để chuyển khoản',
    bank_name       VARCHAR(100)   NOT NULL,
    bank_account_no VARCHAR(30)    NOT NULL,
    account_name    VARCHAR(100)   NOT NULL COMMENT 'Tên chủ TK (khớp với KYC)',
    is_default      TINYINT(1)     NOT NULL DEFAULT 0,
    is_deleted      TINYINT(1)     NOT NULL DEFAULT 0,
    created_at      DATETIME       DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
