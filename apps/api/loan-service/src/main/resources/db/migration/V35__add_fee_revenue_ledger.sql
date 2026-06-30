-- V35: Sổ cái doanh thu phí — hạch toán phí thẩm định + VAT thu được mỗi lần giải ngân.
-- Tiền phí thực tế đọng ở tài khoản tổng VNFITE tại MB; bảng này chỉ ghi sổ để đối soát/báo cáo,
-- KHÔNG di chuyển tiền. Mỗi khoản một dòng (unique loan_id → idempotent khi retry giải ngân).
CREATE TABLE IF NOT EXISTS fee_revenue_ledger (
    id                 VARCHAR(36)     NOT NULL,
    loan_id            VARCHAR(36)     NOT NULL,
    loan_code          VARCHAR(50)     DEFAULT NULL,
    borrower_id        VARCHAR(36)     DEFAULT NULL,
    loan_amount        DECIMAL(15,0)   NOT NULL COMMENT 'Số tiền khoản đã giải ngân (gốc)',
    appraisal_fee_rate DECIMAL(5,2)    DEFAULT NULL COMMENT 'Tỷ lệ phí thẩm định (%)',
    appraisal_fee      DECIMAL(15,0)   NOT NULL COMMENT 'Phí thẩm định (chưa gồm VAT)',
    vat_amount         DECIMAL(15,0)   NOT NULL COMMENT 'VAT 10% trên phí thẩm định',
    total_fee          DECIMAL(15,0)   NOT NULL COMMENT 'Tổng phí = phí thẩm định + VAT',
    disbursed_at       DATETIME        NOT NULL COMMENT 'Mốc ghi nhận doanh thu',
    disbursed_by       VARCHAR(100)    DEFAULT NULL,
    is_deleted         TINYINT(1)      NOT NULL DEFAULT 0,
    created_at         DATETIME        DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_fee_revenue_loan (loan_id),
    INDEX idx_fee_revenue_disbursed (disbursed_at),
    INDEX idx_fee_revenue_borrower  (borrower_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Backfill: hạch toán doanh thu phí cho các khoản ĐÃ giải ngân thực qua hệ thống mới
-- (có disbursed_at). Chỉ insert vào bảng mới, idempotent qua NOT EXISTS + unique loan_id.
-- Khoản migrate từ hệ thống cũ (disbursed_at NULL) bị loại — phí của chúng do hệ thống cũ thu.
INSERT INTO fee_revenue_ledger
    (id, loan_id, loan_code, borrower_id, loan_amount, appraisal_fee_rate,
     appraisal_fee, vat_amount, total_fee, disbursed_at, disbursed_by)
SELECT
    UUID(), l.id, CONCAT('VNF', LPAD(l.loan_seq, 6, '0')), l.borrower_id,
    ROUND(l.amount, 0), l.appraisal_fee_rate,
    ROUND(l.appraisal_fee, 0), ROUND(l.vat_amount, 0), ROUND(l.total_fee, 0),
    l.disbursed_at, l.disbursed_by
FROM loan_requests l
WHERE l.is_deleted = 0
  AND l.status IN ('DISBURSED', 'REPAYING', 'COMPLETED', 'DEFAULTED')
  AND l.total_fee IS NOT NULL AND l.total_fee > 0
  AND l.disbursed_at IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM fee_revenue_ledger f WHERE f.loan_id = l.id);
