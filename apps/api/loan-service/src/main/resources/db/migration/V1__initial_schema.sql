CREATE TABLE IF NOT EXISTS loan_requests (
  id            VARCHAR(36)    PRIMARY KEY,
  borrower_id   VARCHAR(36)    NOT NULL,
  amount        DECIMAL(15,2)  NOT NULL,
  interest_rate DECIMAL(5,2)   NOT NULL,
  term_months   INT            NOT NULL,
  purpose       VARCHAR(500)   NOT NULL,
  status        ENUM('PENDING','ACTIVE','FUNDED','REPAYING','COMPLETED','DEFAULTED') NOT NULL DEFAULT 'PENDING',
  funded_amount DECIMAL(15,2)  NOT NULL DEFAULT 0,
  is_deleted    TINYINT(1)     NOT NULL DEFAULT 0,
  created_at    DATETIME       DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_loan_borrower (borrower_id),
  KEY idx_loan_status   (status),
  KEY idx_loan_created  (created_at)
);

CREATE TABLE IF NOT EXISTS loan_offers (
  id              VARCHAR(36)   PRIMARY KEY,
  loan_request_id VARCHAR(36)   NOT NULL,
  investor_id     VARCHAR(36)   NOT NULL,
  amount          DECIMAL(15,2) NOT NULL,
  status          ENUM('PENDING','ACCEPTED','REJECTED','CANCELLED') NOT NULL DEFAULT 'ACCEPTED',
  is_deleted      TINYINT(1)    NOT NULL DEFAULT 0,
  created_at      DATETIME      DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_offer_loan     (loan_request_id),
  KEY idx_offer_investor (investor_id),
  KEY idx_offer_status   (status)
);
