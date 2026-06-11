-- ─── document_analyses ───────────────────────────────────────────────────────
-- Kết quả AI phân tích chứng từ thu nhập (sao kê lương, HĐLĐ, ĐKKD...)
-- AI chỉ cảnh báo mức độ tin cậy — quyết định cuối cùng thuộc admin thẩm định
CREATE TABLE IF NOT EXISTS document_analyses (
    id              VARCHAR(36)  NOT NULL PRIMARY KEY,
    user_id         VARCHAR(36)  NOT NULL,
    loan_request_id BIGINT       NULL,
    doc_type        VARCHAR(30)  NOT NULL COMMENT 'SALARY_STATEMENT | BANK_STATEMENT | LABOR_CONTRACT | BUSINESS_LICENSE | OTHER',
    file_name       VARCHAR(255) NULL,
    verdict         VARCHAR(20)  NOT NULL COMMENT 'CONSISTENT | SUSPICIOUS | HIGH_RISK | UNREADABLE',
    trust_score     INT          NULL COMMENT 'Độ tin cậy 0-100 do AI ước lượng',
    extracted_data  TEXT         NULL COMMENT 'JSON toàn bộ kết quả AI (extracted fields, findings...)',
    summary         TEXT         NULL,
    is_deleted      TINYINT(1)   NOT NULL DEFAULT 0,
    created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_da_user (user_id),
    INDEX idx_da_loan (loan_request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
