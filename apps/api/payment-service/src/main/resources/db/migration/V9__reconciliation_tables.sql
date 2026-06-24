CREATE TABLE IF NOT EXISTS reconciliation_sessions (
    id              VARCHAR(36)  NOT NULL PRIMARY KEY,
    recon_date      DATE         NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'RUNNING',
    total_items     INT          NOT NULL DEFAULT 0,
    open_items      INT          NOT NULL DEFAULT 0,
    run_by          VARCHAR(100),
    error_message   TEXT,
    is_deleted      TINYINT(1)   NOT NULL DEFAULT 0,
    created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_recon_date (recon_date DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS reconciliation_items (
    id                  VARCHAR(36)     NOT NULL PRIMARY KEY,
    session_id          VARCHAR(36)     NOT NULL,
    item_type           VARCHAR(60)     NOT NULL,
    severity            VARCHAR(10)     NOT NULL DEFAULT 'MEDIUM',
    wallet_id           VARCHAR(36),
    transaction_id      VARCHAR(36),
    reference_id        VARCHAR(200),
    external_ref        VARCHAR(200),
    vnfite_status       VARCHAR(30),
    mb_status           VARCHAR(50),
    amount              DECIMAL(15,2),
    description         TEXT,
    status              VARCHAR(20)     NOT NULL DEFAULT 'OPEN',
    resolved_by         VARCHAR(100),
    resolved_at         DATETIME,
    resolution_notes    TEXT,
    is_deleted          TINYINT(1)      NOT NULL DEFAULT 0,
    created_at          DATETIME        DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_recon_item_session FOREIGN KEY (session_id) REFERENCES reconciliation_sessions (id),
    INDEX idx_recon_item_session (session_id),
    INDEX idx_recon_item_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
