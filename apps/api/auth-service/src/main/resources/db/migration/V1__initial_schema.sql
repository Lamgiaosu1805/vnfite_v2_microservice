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

CREATE TABLE IF NOT EXISTS kyc_documents (
  id         VARCHAR(36)  PRIMARY KEY,
  user_id    VARCHAR(36)  NOT NULL,
  doc_type   ENUM('NATIONAL_ID','PASSPORT','DRIVING_LICENSE','UTILITY_BILL','BANK_STATEMENT') NOT NULL,
  doc_url    VARCHAR(500) NOT NULL,
  status     ENUM('NONE','PENDING','APPROVED','REJECTED') NOT NULL DEFAULT 'PENDING',
  is_deleted TINYINT(1)   NOT NULL DEFAULT 0,
  created_at DATETIME     DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_kyc_doc_user_id (user_id),
  KEY idx_kyc_doc_status  (status)
);

CREATE TABLE IF NOT EXISTS refresh_tokens (
  id         VARCHAR(36)  PRIMARY KEY,
  user_id    VARCHAR(36)  NOT NULL,
  token      VARCHAR(500) NOT NULL,
  expires_at DATETIME     NOT NULL,
  created_at DATETIME     DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_refresh_token (token)
);

CREATE TABLE IF NOT EXISTS password_reset_tokens (
  id         VARCHAR(36)  PRIMARY KEY,
  user_id    VARCHAR(36)  NOT NULL,
  token      VARCHAR(500) NOT NULL,
  expires_at DATETIME     NOT NULL,
  used       TINYINT(1)   NOT NULL DEFAULT 0,
  created_at DATETIME     DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_reset_token (token),
  KEY idx_prt_user_id (user_id)
);
