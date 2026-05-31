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

CREATE TABLE IF NOT EXISTS cms_admin_users (
  id         VARCHAR(36)  PRIMARY KEY,
  username   VARCHAR(60)  NOT NULL,
  email      VARCHAR(150) NOT NULL,
  full_name  VARCHAR(100) NOT NULL,
  password   VARCHAR(255) NOT NULL,
  role       VARCHAR(20)  NOT NULL,
  active     TINYINT(1)   NOT NULL DEFAULT 1,
  is_deleted TINYINT(1)   NOT NULL DEFAULT 0,
  created_at DATETIME     DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_cms_admin_username (username),
  UNIQUE KEY uq_cms_admin_email (email),
  KEY idx_cms_admin_role (role)
);
