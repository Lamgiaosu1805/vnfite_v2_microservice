CREATE TABLE IF NOT EXISTS match_records (
  id          VARCHAR(36)   PRIMARY KEY,
  loan_id     VARCHAR(36)   NOT NULL,
  investor_id VARCHAR(36)   NOT NULL,
  score       DECIMAL(4,3)  NOT NULL,
  status      ENUM('PENDING','NOTIFIED','ACCEPTED','REJECTED','EXPIRED') NOT NULL DEFAULT 'PENDING',
  is_deleted  TINYINT(1)    NOT NULL DEFAULT 0,
  created_at  DATETIME      DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_match_loan_investor (loan_id, investor_id),
  KEY idx_match_loan     (loan_id),
  KEY idx_match_investor (investor_id),
  KEY idx_match_status   (status),
  KEY idx_match_score    (score)
);

CREATE TABLE IF NOT EXISTS investor_preferences (
  id                    VARCHAR(36)   PRIMARY KEY,
  investor_id           VARCHAR(36)   NOT NULL,
  min_investment_amount DECIMAL(15,2) NOT NULL,
  max_investment_amount DECIMAL(15,2) NOT NULL,
  min_interest_rate     DECIMAL(5,2)  NOT NULL,
  max_interest_rate     DECIMAL(5,2),
  min_term_months       INT           NOT NULL,
  max_term_months       INT           NOT NULL,
  active                TINYINT(1)    NOT NULL DEFAULT 1,
  is_deleted            TINYINT(1)    NOT NULL DEFAULT 0,
  created_at            DATETIME      DEFAULT CURRENT_TIMESTAMP,
  updated_at            DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_pref_investor (investor_id),
  KEY idx_pref_active   (active)
);

CREATE TABLE IF NOT EXISTS pending_loans (
  loan_id         VARCHAR(36)   PRIMARY KEY,
  borrower_id     VARCHAR(36)   NOT NULL,
  amount          DECIMAL(15,2) NOT NULL,
  interest_rate   DECIMAL(5,2)  NOT NULL,
  term_months     INT           NOT NULL,
  purpose         VARCHAR(500),
  fully_funded    TINYINT(1)    NOT NULL DEFAULT 0,
  is_deleted      TINYINT(1)    NOT NULL DEFAULT 0,
  received_at     DATETIME      DEFAULT CURRENT_TIMESTAMP,
  last_matched_at DATETIME,
  updated_at      DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_pending_funded       (fully_funded),
  KEY idx_pending_last_matched (last_matched_at)
);
