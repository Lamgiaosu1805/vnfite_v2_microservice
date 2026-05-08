-- P2P Lending Platform — database initialisation
-- Runs once on first MySQL container start via docker-entrypoint-initdb.d

SET NAMES utf8mb4;

-- ─── AUTH DB ──────────────────────────────────────────────────────
CREATE DATABASE IF NOT EXISTS auth_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE auth_db;

CREATE TABLE IF NOT EXISTS users (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  email VARCHAR(255) NOT NULL UNIQUE,
  password VARCHAR(255) NOT NULL,
  full_name VARCHAR(255),
  phone VARCHAR(20),
  role ENUM('BORROWER','LENDER','ADMIN') NOT NULL,
  kyc_status ENUM('PENDING','SUBMITTED','APPROVED','REJECTED') DEFAULT 'PENDING',
  is_active BOOLEAN DEFAULT TRUE,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS kyc_documents (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  doc_type VARCHAR(50),
  doc_url VARCHAR(500),
  status ENUM('PENDING','APPROVED','REJECTED') DEFAULT 'PENDING',
  reviewed_by BIGINT,
  reviewed_at TIMESTAMP,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS refresh_tokens (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  token VARCHAR(500) NOT NULL UNIQUE,
  expires_at TIMESTAMP NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ─── LOAN DB ──────────────────────────────────────────────────────
CREATE DATABASE IF NOT EXISTS loan_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE loan_db;

CREATE TABLE IF NOT EXISTS loan_requests (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  borrower_id BIGINT NOT NULL,
  amount DECIMAL(15,2) NOT NULL,
  interest_rate DECIMAL(5,2) NOT NULL,
  term_months INT NOT NULL,
  purpose VARCHAR(500),
  status ENUM('PENDING','APPROVED','ACTIVE','FUNDED','REPAYING','COMPLETED','REJECTED','DEFAULTED') DEFAULT 'PENDING',
  funded_amount DECIMAL(15,2) DEFAULT 0,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS loan_offers (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  loan_request_id BIGINT NOT NULL,
  investor_id BIGINT NOT NULL,
  amount DECIMAL(15,2) NOT NULL,
  status ENUM('PENDING','ACCEPTED','REJECTED','CANCELLED') DEFAULT 'PENDING',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ─── CUSTOMER DB ──────────────────────────────────────────────────
CREATE DATABASE IF NOT EXISTS customer_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE customer_db;

CREATE TABLE IF NOT EXISTS customer_profiles (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL UNIQUE,
  email VARCHAR(255) NOT NULL,
  full_name VARCHAR(255),
  phone VARCHAR(20),
  role VARCHAR(20),
  kyc_status VARCHAR(20) DEFAULT 'PENDING',
  credit_score INT DEFAULT 0,
  total_borrowed DECIMAL(15,2) DEFAULT 0,
  total_invested DECIMAL(15,2) DEFAULT 0,
  is_active BOOLEAN DEFAULT TRUE,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS kyc_reviews (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  reviewer_id BIGINT NOT NULL,
  action ENUM('APPROVE','REJECT') NOT NULL,
  note TEXT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ─── MATCHING DB ──────────────────────────────────────────────────
CREATE DATABASE IF NOT EXISTS matching_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE matching_db;

CREATE TABLE IF NOT EXISTS match_records (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  loan_request_id BIGINT NOT NULL,
  investor_id BIGINT NOT NULL,
  match_score DECIMAL(5,2),
  status ENUM('SUGGESTED','ACCEPTED','REJECTED') DEFAULT 'SUGGESTED',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS investor_preferences (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  investor_id BIGINT NOT NULL UNIQUE,
  min_amount DECIMAL(15,2),
  max_amount DECIMAL(15,2),
  min_interest_rate DECIMAL(5,2),
  max_term_months INT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- ─── PAYMENT DB ───────────────────────────────────────────────────
CREATE DATABASE IF NOT EXISTS payment_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE payment_db;

CREATE TABLE IF NOT EXISTS transactions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  loan_id BIGINT NOT NULL,
  from_user_id BIGINT NOT NULL,
  to_user_id BIGINT NOT NULL,
  amount DECIMAL(15,2) NOT NULL,
  type ENUM('DISBURSEMENT','REPAYMENT','FEE') NOT NULL,
  status ENUM('PENDING','COMPLETED','FAILED') DEFAULT 'PENDING',
  reference_no VARCHAR(100) UNIQUE,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS repayment_schedules (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  loan_id BIGINT NOT NULL,
  due_date DATE NOT NULL,
  amount DECIMAL(15,2) NOT NULL,
  principal DECIMAL(15,2),
  interest DECIMAL(15,2),
  status ENUM('PENDING','PAID','OVERDUE') DEFAULT 'PENDING',
  paid_at TIMESTAMP,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ─── NOTIFICATION DB ──────────────────────────────────────────────
CREATE DATABASE IF NOT EXISTS notification_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE notification_db;

CREATE TABLE IF NOT EXISTS notifications (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  title VARCHAR(255) NOT NULL,
  message TEXT NOT NULL,
  type ENUM('EMAIL','SMS','PUSH','IN_APP') NOT NULL,
  channel VARCHAR(50),
  is_read BOOLEAN DEFAULT FALSE,
  sent_at TIMESTAMP,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS notification_templates (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(100) NOT NULL UNIQUE,
  title VARCHAR(255),
  body TEXT,
  type VARCHAR(50),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ─── Grant privileges ─────────────────────────────────────────────
GRANT ALL PRIVILEGES ON auth_db.*         TO 'p2p_user'@'%';
GRANT ALL PRIVILEGES ON loan_db.*         TO 'p2p_user'@'%';
GRANT ALL PRIVILEGES ON customer_db.*     TO 'p2p_user'@'%';
GRANT ALL PRIVILEGES ON matching_db.*     TO 'p2p_user'@'%';
GRANT ALL PRIVILEGES ON payment_db.*      TO 'p2p_user'@'%';
GRANT ALL PRIVILEGES ON notification_db.* TO 'p2p_user'@'%';
FLUSH PRIVILEGES;
