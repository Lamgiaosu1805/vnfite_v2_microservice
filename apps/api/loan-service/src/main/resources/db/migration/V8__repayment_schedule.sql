-- Khối 1 — Hạ tầng trả nợ + DPD (gộp trong loan_db, tách payment-service sau)
-- Kiểu trả nợ là thuộc tính sản phẩm: EMI_MONTHLY (gốc+lãi đều) mặc định cho mọi sản phẩm hiện có.
ALTER TABLE loan_products
    ADD COLUMN repayment_method VARCHAR(40) NOT NULL DEFAULT 'EMI_MONTHLY' AFTER late_fee_rate;

-- Lịch trả nợ: sinh ra khi khoản vay FUNDED. Mỗi dòng là một kỳ.
CREATE TABLE IF NOT EXISTS repayment_schedule (
  id            VARCHAR(36)   PRIMARY KEY,
  loan_id       VARCHAR(36)   NOT NULL,
  period_number INT           NOT NULL,
  due_date      DATE          NOT NULL,
  principal_due DECIMAL(15,2) NOT NULL,
  interest_due  DECIMAL(15,2) NOT NULL,
  total_due     DECIMAL(15,2) NOT NULL,
  paid_amount   DECIMAL(15,2) NOT NULL DEFAULT 0,
  paid_at       DATETIME      NULL,
  status        ENUM('PENDING','PARTIAL','PAID','OVERDUE') NOT NULL DEFAULT 'PENDING',
  -- Số ngày quá hạn của riêng kỳ này — job DPD cập nhật hàng ngày.
  dpd           INT           NOT NULL DEFAULT 0,
  is_deleted    TINYINT(1)    NOT NULL DEFAULT 0,
  created_at    DATETIME      DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_schedule_loan_period (loan_id, period_number),
  KEY idx_schedule_loan     (loan_id),
  KEY idx_schedule_due      (due_date),
  KEY idx_schedule_status   (status)
);

-- Giao dịch trả nợ: mỗi lần ghi nhận tiền về (đối tác thu hộ hoặc admin nhập tay).
CREATE TABLE IF NOT EXISTS repayment_transaction (
  id           VARCHAR(36)   PRIMARY KEY,
  loan_id      VARCHAR(36)   NOT NULL,
  schedule_id  VARCHAR(36)   NULL,
  amount       DECIMAL(15,2) NOT NULL,
  paid_at      DATETIME      NOT NULL,
  channel      ENUM('COLLECTION_PARTNER','MANUAL_ADMIN') NOT NULL DEFAULT 'MANUAL_ADMIN',
  external_ref VARCHAR(100)  NULL,
  recorded_by  VARCHAR(36)   NULL,
  note         VARCHAR(500)  NULL,
  is_deleted   TINYINT(1)    NOT NULL DEFAULT 0,
  created_at   DATETIME      DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_repay_txn_loan     (loan_id),
  KEY idx_repay_txn_schedule (schedule_id),
  KEY idx_repay_txn_paid_at  (paid_at)
);
