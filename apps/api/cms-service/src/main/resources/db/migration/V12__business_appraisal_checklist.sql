-- V12: Checklist thẩm định riêng cho khoản gọi vốn HKD/DN.
-- Lưu kết quả thẩm định vận hành trong cms_db, không mirror dữ liệu loan/auth.

CREATE TABLE IF NOT EXISTS business_appraisal_checklist (
    id             VARCHAR(36)  NOT NULL,
    loan_id        VARCHAR(36)  NOT NULL,
    checklist_code VARCHAR(80)  NOT NULL,
    category       VARCHAR(80)  NOT NULL,
    title          VARCHAR(255) NOT NULL,
    instruction    TEXT         NULL,
    required       TINYINT(1)   NOT NULL DEFAULT 0,
    status         VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
    note           TEXT         NULL,
    evidence_refs  TEXT         NULL,
    updated_by     VARCHAR(100) NULL,
    is_deleted     TINYINT(1)   NOT NULL DEFAULT 0,
    created_at     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_business_appraisal_loan_code (loan_id, checklist_code),
    KEY idx_business_appraisal_loan (loan_id),
    KEY idx_business_appraisal_status (status),
    KEY idx_business_appraisal_updated (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
