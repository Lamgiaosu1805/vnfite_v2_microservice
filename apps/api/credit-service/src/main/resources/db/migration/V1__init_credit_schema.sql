-- ─── borrower_profiles ───────────────────────────────────────────────────────
-- Hồ sơ tài chính tự khai của người gọi vốn (thu thập khi nộp đơn vay lần đầu)
CREATE TABLE IF NOT EXISTS borrower_profiles (
    id                    VARCHAR(36)    NOT NULL PRIMARY KEY,
    user_id               VARCHAR(36)    NOT NULL UNIQUE COMMENT 'FK → auth_db.users.id',
    occupation_type       VARCHAR(30)    NULL COMMENT 'GOV_EMPLOYEE | SALARIED | BUSINESS_OWNER | FREELANCER | OTHER',
    employment_years      DECIMAL(4,1)   NULL,
    monthly_income        DECIMAL(15,2)  NULL COMMENT 'Thu nhập hàng tháng (VND)',
    marital_status        VARCHAR(20)    NULL COMMENT 'SINGLE | MARRIED | DIVORCED | WIDOWED',
    dependents_count      INT            NULL,
    education_level       VARCHAR(20)    NULL COMMENT 'POSTGRAD | UNIVERSITY | COLLEGE | HIGH_SCHOOL | OTHER',
    existing_monthly_debt DECIMAL(15,2)  NULL COMMENT 'Nghĩa vụ trả nợ hàng tháng hiện tại (VND)',
    notes                 VARCHAR(500)   NULL,
    is_deleted            TINYINT(1)     NOT NULL DEFAULT 0,
    created_at            DATETIME       DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_bp_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── scoring_criteria ────────────────────────────────────────────────────────
-- Scorecard cấu hình được: mỗi dòng là một band điểm của một tiêu chí
CREATE TABLE IF NOT EXISTS scoring_criteria (
    id            VARCHAR(36)    NOT NULL PRIMARY KEY,
    criteria_code VARCHAR(50)    NOT NULL COMMENT 'AGE, MONTHLY_INCOME, DTI_RATIO...',
    criteria_name VARCHAR(150)   NOT NULL,
    component     VARCHAR(30)    NOT NULL COMMENT 'DEMOGRAPHIC | INCOME | CREDIT_HISTORY | PLATFORM | LOAN',
    band_label    VARCHAR(100)   NOT NULL,
    min_value     DECIMAL(15,2)  NULL COMMENT 'Numeric band: min <= v (null = không chặn dưới)',
    max_value     DECIMAL(15,2)  NULL COMMENT 'Numeric band: v < max (null = không chặn trên)',
    match_value   VARCHAR(50)    NULL COMMENT 'Categorical band: so khớp chuỗi',
    points        INT            NOT NULL,
    active        TINYINT(1)     NOT NULL DEFAULT 1,
    is_deleted    TINYINT(1)     NOT NULL DEFAULT 0,
    created_at    DATETIME       DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_criteria_band (criteria_code, band_label),
    INDEX idx_sc_code (criteria_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── credit_scores ───────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS credit_scores (
    id                VARCHAR(36)  NOT NULL PRIMARY KEY,
    user_id           VARCHAR(36)  NOT NULL COMMENT 'FK → auth_db.users.id',
    loan_request_id   BIGINT       NULL COMMENT 'FK → loan_db.loan_requests.id (null = pre-score)',
    score             INT          NOT NULL COMMENT 'Điểm chuẩn hóa 300-850',
    grade             VARCHAR(2)   NOT NULL COMMENT 'A | B | C | D | E',
    raw_points        INT          NOT NULL,
    max_points        INT          NOT NULL,
    model_version     VARCHAR(30)  NOT NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'VALID' COMMENT 'VALID | SUPERSEDED | EXPIRED',
    ai_summary        TEXT         NULL COMMENT 'Tóm tắt rủi ro AI sinh (tư vấn)',
    ai_risk_flags     TEXT         NULL COMMENT 'JSON array cờ rủi ro',
    ai_recommendation TEXT         NULL,
    expires_at        DATETIME     NOT NULL,
    is_deleted        TINYINT(1)   NOT NULL DEFAULT 0,
    created_at        DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_cs_user_status (user_id, status),
    INDEX idx_cs_loan (loan_request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── credit_score_details ────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS credit_score_details (
    id              VARCHAR(36)  NOT NULL PRIMARY KEY,
    credit_score_id VARCHAR(36)  NOT NULL,
    criteria_code   VARCHAR(50)  NOT NULL,
    criteria_name   VARCHAR(150) NULL,
    component       VARCHAR(30)  NOT NULL,
    raw_value       VARCHAR(255) NULL,
    points          INT          NOT NULL,
    max_points      INT          NOT NULL,
    is_deleted      TINYINT(1)   NOT NULL DEFAULT 0,
    created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_csd_score (credit_score_id),
    CONSTRAINT fk_csd_score FOREIGN KEY (credit_score_id) REFERENCES credit_scores (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── feature_snapshots ───────────────────────────────────────────────────────
-- Training data cho ML model tương lai: features lúc chấm + outcome khi khoản vay kết thúc
CREATE TABLE IF NOT EXISTS feature_snapshots (
    id              VARCHAR(36)  NOT NULL PRIMARY KEY,
    user_id         VARCHAR(36)  NOT NULL,
    loan_request_id BIGINT       NULL,
    credit_score_id VARCHAR(36)  NULL,
    features        TEXT         NOT NULL COMMENT 'JSON toàn bộ features tại thời điểm chấm',
    loan_outcome    VARCHAR(20)  NULL COMMENT 'COMPLETED | DEFAULTED — label điền sau',
    outcome_at      DATETIME     NULL,
    is_deleted      TINYINT(1)   NOT NULL DEFAULT 0,
    created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_fs_user (user_id),
    INDEX idx_fs_loan (loan_request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
