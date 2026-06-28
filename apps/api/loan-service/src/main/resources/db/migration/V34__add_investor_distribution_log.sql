-- V34: Bảng ghi lại chi tiết phân bổ tiền cho từng nhà đầu tư mỗi lần trả nợ
-- Lưu: gross amount, phần gốc/lãi/phí phạt, thuế TNCN 5%, số tiền thực nhận
CREATE TABLE IF NOT EXISTS investor_distribution_log (
    id                       VARCHAR(36)     NOT NULL,
    repayment_transaction_id VARCHAR(36)     NOT NULL COMMENT 'FK → repayment_transaction.id',
    loan_id                  VARCHAR(36)     NOT NULL,
    loan_code                VARCHAR(50)     DEFAULT NULL,
    schedule_id              VARCHAR(36)     DEFAULT NULL,
    offer_id                 VARCHAR(36)     NOT NULL,
    investor_id              VARCHAR(36)     NOT NULL,
    gross_amount             DECIMAL(15,0)   NOT NULL COMMENT 'Tổng phần phân bổ trước thuế',
    principal_amount         DECIMAL(15,0)   NOT NULL COMMENT 'Phần gốc (không chịu thuế)',
    interest_amount          DECIMAL(15,0)   NOT NULL COMMENT 'Phần lãi (chịu thuế TNCN)',
    late_fee_amount          DECIMAL(15,0)   NOT NULL COMMENT 'Phần phí phạt (chịu thuế TNCN)',
    tax_rate                 DECIMAL(5,4)    NOT NULL COMMENT 'Tỷ lệ thuế áp dụng, vd 0.0500',
    tax_amount               DECIMAL(15,0)   NOT NULL COMMENT 'Số tiền thuế TNCN khấu trừ',
    net_amount               DECIMAL(15,0)   NOT NULL COMMENT 'Số tiền thực cộng vào ví nhà đầu tư',
    credit_ref               VARCHAR(200)    DEFAULT NULL,
    distributed_at           DATETIME        NOT NULL,
    is_deleted               TINYINT(1)      NOT NULL DEFAULT 0,
    created_at               DATETIME        DEFAULT CURRENT_TIMESTAMP,
    updated_at               DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_inv_dist_loan       (loan_id),
    INDEX idx_inv_dist_investor   (investor_id),
    INDEX idx_inv_dist_txn        (repayment_transaction_id),
    INDEX idx_inv_dist_distributed (distributed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
