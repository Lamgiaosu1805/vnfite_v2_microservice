-- P2P Lending Platform — database initialisation
-- Runs once on first MySQL container start via docker-entrypoint-initdb.d

SET NAMES utf8mb4;

-- ─── AUTH DB ──────────────────────────────────────────────────────
CREATE DATABASE IF NOT EXISTS auth_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE auth_db;

CREATE TABLE IF NOT EXISTS users (
  id            VARCHAR(36)  PRIMARY KEY,
  phone         VARCHAR(20)  NOT NULL,
  password      VARCHAR(255) NOT NULL,
  full_name     VARCHAR(100),
  email         VARCHAR(150),
  role          ENUM('USER','ADMIN') NOT NULL DEFAULT 'USER',
  kyc_status    ENUM('NONE','PENDING','APPROVED','REJECTED') NOT NULL DEFAULT 'NONE',
  referred_by   VARCHAR(20),
  is_deleted    TINYINT(1)   NOT NULL DEFAULT 0,
  created_at    DATETIME     DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_users_phone (phone),
  KEY idx_users_email (email)
);

CREATE TABLE IF NOT EXISTS kyc_documents (
  id         VARCHAR(36)  PRIMARY KEY,
  user_id    VARCHAR(36)  NOT NULL,
  doc_type   ENUM('NATIONAL_ID','PASSPORT','DRIVING_LICENSE','UTILITY_BILL','BANK_STATEMENT') NOT NULL,
  doc_url    VARCHAR(500) NOT NULL,
  status     ENUM('NONE','PENDING','APPROVED','REJECTED') NOT NULL DEFAULT 'PENDING',
  is_deleted TINYINT(1)   NOT NULL DEFAULT 0,
  created_at DATETIME     DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_kyc_user_id (user_id),
  KEY idx_kyc_status  (status)
);

CREATE TABLE IF NOT EXISTS refresh_tokens (
  id         VARCHAR(36)  PRIMARY KEY,
  user_id    VARCHAR(36)  NOT NULL,
  token      VARCHAR(500) NOT NULL,
  expires_at DATETIME     NOT NULL,
  created_at DATETIME     DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_refresh_token (token)
);

CREATE TABLE IF NOT EXISTS kyc_submissions (
  id                VARCHAR(36)  PRIMARY KEY,
  user_id           VARCHAR(36)  NOT NULL,
  cccd_number       VARCHAR(20)  NOT NULL,
  full_name         VARCHAR(100) NOT NULL,
  gender            ENUM('MALE','FEMALE') NOT NULL,
  date_of_birth     DATE         NOT NULL,
  permanent_address VARCHAR(500) NOT NULL,
  hometown          VARCHAR(255) NOT NULL,
  issue_date        DATE         NOT NULL,
  issuing_authority VARCHAR(255) NOT NULL,
  expiry_date       DATE,
  front_image_id    VARCHAR(255) NOT NULL,
  back_image_id     VARCHAR(255) NOT NULL,
  portrait_image_id VARCHAR(255) NOT NULL,
  status            ENUM('NONE','PENDING','APPROVED','REJECTED') NOT NULL DEFAULT 'PENDING',
  is_deleted        TINYINT(1)   NOT NULL DEFAULT 0,
  created_at        DATETIME     DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_kyc_cccd (cccd_number),
  KEY idx_kyc_user_id (user_id),
  KEY idx_kyc_status  (status)
);

-- ─── LOAN DB ──────────────────────────────────────────────────────
CREATE DATABASE IF NOT EXISTS loan_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE loan_db;

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

-- ─── CUSTOMER DB (CMS read views) ─────────────────────────────────
CREATE DATABASE IF NOT EXISTS customer_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE customer_db;

CREATE TABLE IF NOT EXISTS cms_users (
  user_id        VARCHAR(36)  PRIMARY KEY,
  phone          VARCHAR(20),
  email          VARCHAR(150),
  full_name      VARCHAR(100),
  role           VARCHAR(20)  NOT NULL,
  kyc_status     VARCHAR(20)  NOT NULL DEFAULT 'NONE',
  account_status ENUM('ACTIVE','SUSPENDED','LOCKED') NOT NULL DEFAULT 'ACTIVE',
  is_deleted     TINYINT(1)   NOT NULL DEFAULT 0,
  created_at     DATETIME     DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_cu_email     (email),
  KEY idx_cu_kycstatus (kyc_status),
  KEY idx_cu_role      (role),
  KEY idx_cu_status    (account_status)
);

CREATE TABLE IF NOT EXISTS cms_loans (
  loan_id       VARCHAR(36)   PRIMARY KEY,
  borrower_id   VARCHAR(36)   NOT NULL,
  amount        DECIMAL(15,2) NOT NULL,
  interest_rate DECIMAL(5,2)  NOT NULL,
  term_months   INT           NOT NULL,
  purpose       VARCHAR(500),
  status        VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
  is_deleted    TINYINT(1)    NOT NULL DEFAULT 0,
  created_at    DATETIME      DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_cl_borrower (borrower_id),
  KEY idx_cl_status   (status),
  KEY idx_cl_created  (created_at)
);

CREATE TABLE IF NOT EXISTS daily_stats (
  id           VARCHAR(36)   PRIMARY KEY,
  stat_date    DATE          NOT NULL,
  new_users    BIGINT        NOT NULL DEFAULT 0,
  new_loans    BIGINT        NOT NULL DEFAULT 0,
  funded_loans BIGINT        NOT NULL DEFAULT 0,
  loan_volume  DECIMAL(18,2) NOT NULL DEFAULT 0,
  is_deleted   TINYINT(1)    NOT NULL DEFAULT 0,
  created_at   DATETIME      DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY idx_ds_date (stat_date)
);

-- ─── MATCHING DB ──────────────────────────────────────────────────
CREATE DATABASE IF NOT EXISTS matching_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE matching_db;

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

-- ─── PAYMENT DB ───────────────────────────────────────────────────
CREATE DATABASE IF NOT EXISTS payment_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE payment_db;

CREATE TABLE IF NOT EXISTS transactions (
  id           VARCHAR(36)   PRIMARY KEY,
  loan_id      VARCHAR(36)   NOT NULL,
  from_user_id VARCHAR(36)   NOT NULL,
  to_user_id   VARCHAR(36)   NOT NULL,
  amount       DECIMAL(15,2) NOT NULL,
  type         ENUM('DISBURSEMENT','REPAYMENT','FEE') NOT NULL,
  status       ENUM('PENDING','COMPLETED','FAILED') NOT NULL DEFAULT 'PENDING',
  reference_no VARCHAR(100),
  is_deleted   TINYINT(1)    NOT NULL DEFAULT 0,
  created_at   DATETIME      DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_reference_no (reference_no)
);

CREATE TABLE IF NOT EXISTS repayment_schedules (
  id          VARCHAR(36)   PRIMARY KEY,
  loan_id     VARCHAR(36)   NOT NULL,
  due_date    DATE          NOT NULL,
  amount      DECIMAL(15,2) NOT NULL,
  principal   DECIMAL(15,2),
  interest    DECIMAL(15,2),
  status      ENUM('PENDING','PAID','OVERDUE') NOT NULL DEFAULT 'PENDING',
  paid_at     DATETIME,
  is_deleted  TINYINT(1)    NOT NULL DEFAULT 0,
  created_at  DATETIME      DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- ─── NOTIFICATION DB ──────────────────────────────────────────────
CREATE DATABASE IF NOT EXISTS notification_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE notification_db;

CREATE TABLE IF NOT EXISTS notifications (
  id         VARCHAR(36)  PRIMARY KEY,
  user_id    VARCHAR(36)  NOT NULL,
  title      VARCHAR(255) NOT NULL,
  message    TEXT         NOT NULL,
  type       ENUM('EMAIL','SMS','PUSH','IN_APP') NOT NULL,
  channel    VARCHAR(50),
  is_read    TINYINT(1)   NOT NULL DEFAULT 0,
  sent_at    DATETIME,
  is_deleted TINYINT(1)   NOT NULL DEFAULT 0,
  created_at DATETIME     DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS notification_templates (
  id         VARCHAR(36)  PRIMARY KEY,
  code       VARCHAR(100) NOT NULL,
  title      VARCHAR(255),
  body       TEXT,
  type       VARCHAR(50),
  is_deleted TINYINT(1)   NOT NULL DEFAULT 0,
  created_at DATETIME     DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_template_code (code)
);

-- ─── Grant privileges ─────────────────────────────────────────────
GRANT ALL PRIVILEGES ON auth_db.*         TO 'p2p_user'@'%';
GRANT ALL PRIVILEGES ON loan_db.*         TO 'p2p_user'@'%';
GRANT ALL PRIVILEGES ON customer_db.*     TO 'p2p_user'@'%';
GRANT ALL PRIVILEGES ON matching_db.*     TO 'p2p_user'@'%';
GRANT ALL PRIVILEGES ON payment_db.*      TO 'p2p_user'@'%';
GRANT ALL PRIVILEGES ON notification_db.* TO 'p2p_user'@'%';
FLUSH PRIVILEGES;
