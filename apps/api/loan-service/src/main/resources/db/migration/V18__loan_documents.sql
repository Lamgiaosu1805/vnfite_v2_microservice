CREATE TABLE IF NOT EXISTS loan_documents (
    id              VARCHAR(36)  NOT NULL,
    loan_request_id VARCHAR(36)  NOT NULL,
    doc_type        VARCHAR(100) NOT NULL COMMENT 'SALARY_STATEMENT | BANK_STATEMENT | BUSINESS_LICENSE | TAX_RETURN | OTHER',
    file_id         VARCHAR(100) NOT NULL COMMENT 'file-manager ObjectId',
    file_name       VARCHAR(255),
    is_deleted      TINYINT(1)   NOT NULL DEFAULT 0,
    created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_loan_doc_loan   (loan_request_id),
    INDEX idx_loan_doc_type   (loan_request_id, doc_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
