-- V8: Decision Audit Log
-- Lưu snapshot điểm số + quyết định tại thời điểm phê duyệt/từ chối.
-- Phục vụ hai mục đích:
--   1. Audit trail tuân thủ (ai, khi nào, số tiền/lãi nào được duyệt)
--   2. Dữ liệu huấn luyện ML Phase 1 (features tại thời điểm quyết định + nhãn APPROVED/REJECTED)

CREATE TABLE loan_decision_audit_log
(
    id                     VARCHAR(36)    NOT NULL PRIMARY KEY,

    -- Định danh khoản gọi vốn
    loan_id                VARCHAR(36)    NOT NULL,
    loan_code              VARCHAR(50),
    borrower_id            VARCHAR(36),

    -- Snapshot khoản vay tại thời điểm quyết định
    requested_amount       DECIMAL(15, 2),
    proposed_amount        DECIMAL(15, 2),
    proposed_interest_rate DECIMAL(5, 2),
    proposed_by            VARCHAR(100),
    final_amount           DECIMAL(15, 2),
    final_interest_rate    DECIMAL(5, 2),
    term_months            INT,
    purpose                VARCHAR(500),
    occupation             VARCHAR(100),
    monthly_income         DECIMAL(15, 2),

    -- Kết quả engine thẩm định
    credit_score           INT,
    credit_band            VARCHAR(5),
    -- JSON đầy đủ của AppraisalSuggestion (features cho ML)
    appraisal_snapshot     JSON,

    -- Quyết định
    decision               VARCHAR(20)    NOT NULL,  -- APPROVED | REJECTED
    rejection_reason       VARCHAR(1000),

    -- Người ra quyết định (ban lãnh đạo cấp 2)
    decided_by             VARCHAR(100)   NOT NULL,
    decided_at             DATETIME       NOT NULL,
    decider_role           VARCHAR(20),

    -- Người đề xuất (thẩm định viên cấp 1) — lưu lại để đánh giá chất lượng thẩm định
    appraiser_username     VARCHAR(100),

    -- Audit fields chuẩn
    is_deleted             TINYINT(1)     NOT NULL DEFAULT 0,
    created_at             DATETIME       DEFAULT CURRENT_TIMESTAMP,
    updated_at             DATETIME       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_audit_loan_id (loan_id),
    INDEX idx_audit_decided_at (decided_at),
    INDEX idx_audit_decision (decision),
    INDEX idx_audit_decided_by (decided_by),
    INDEX idx_audit_credit_band (credit_band)
);
