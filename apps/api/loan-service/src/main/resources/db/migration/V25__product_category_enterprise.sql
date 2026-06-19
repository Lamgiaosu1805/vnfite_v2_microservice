-- V25: thêm ENTERPRISE vào enum category của loan_products
ALTER TABLE loan_products
  MODIFY COLUMN category ENUM('INDIVIDUAL','BUSINESS','ENTERPRISE') NOT NULL DEFAULT 'INDIVIDUAL';
