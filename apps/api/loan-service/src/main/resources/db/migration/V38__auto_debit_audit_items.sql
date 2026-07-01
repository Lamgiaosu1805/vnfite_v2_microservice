CREATE TABLE IF NOT EXISTS repayment_auto_debit_audit_item (
    id               VARCHAR(36)    NOT NULL,
    audit_id         VARCHAR(36)    NOT NULL,
    loan_id          VARCHAR(36)    NOT NULL,
    loan_code        VARCHAR(50)    NULL,
    borrower_id      VARCHAR(36)    NULL,
    result_status    VARCHAR(30)    NOT NULL,
    amount_collected DECIMAL(15,0)  NOT NULL DEFAULT 0,
    message          VARCHAR(500)   NULL,
    is_deleted       TINYINT(1)     NOT NULL DEFAULT 0,
    created_at       DATETIME       DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_auto_debit_item_audit (audit_id),
    KEY idx_auto_debit_item_loan (loan_id),
    KEY idx_auto_debit_item_borrower (borrower_id),
    KEY idx_auto_debit_item_status (result_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
