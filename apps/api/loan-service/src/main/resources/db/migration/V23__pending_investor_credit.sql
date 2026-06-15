-- Hàng đợi đối soát: khi cộng tiền hoàn trả vào ví một nhà đầu tư thất bại (lỗi mạng/ví),
-- ghi lại để job đối soát thử lại idempotent (theo reference_id) thay vì chỉ log.
-- Khi cộng lại thành công → status COMPLETED + thông báo cho nhà đầu tư.
CREATE TABLE IF NOT EXISTS pending_investor_credit (
  id            VARCHAR(36)   PRIMARY KEY,
  loan_id       VARCHAR(36)   NOT NULL,
  loan_code     VARCHAR(40)   NULL,
  investor_id   VARCHAR(36)   NOT NULL,
  offer_id      VARCHAR(36)   NOT NULL,
  amount        DECIMAL(15,2) NOT NULL,
  reference_id  VARCHAR(100)  NOT NULL,
  description   VARCHAR(500)  NULL,
  status        ENUM('PENDING','COMPLETED') NOT NULL DEFAULT 'PENDING',
  attempts      INT           NOT NULL DEFAULT 0,
  last_error    VARCHAR(500)  NULL,
  is_deleted    TINYINT(1)    NOT NULL DEFAULT 0,
  created_at    DATETIME      DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_pending_credit_ref (reference_id),
  KEY idx_pending_credit_status (status),
  KEY idx_pending_credit_loan   (loan_id)
);
