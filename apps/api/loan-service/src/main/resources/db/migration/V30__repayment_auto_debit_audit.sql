-- Audit từng lượt quét thu nợ tự động/thủ công.
-- Chỉ ghi log vận hành, không tác động dữ liệu tiền hoặc lịch trả nợ.
CREATE TABLE IF NOT EXISTS repayment_auto_debit_audit (
  id                 VARCHAR(36)   PRIMARY KEY,
  trigger_source     VARCHAR(30)   NOT NULL,
  triggered_by       VARCHAR(100)  NULL,
  started_at         DATETIME      NOT NULL,
  finished_at        DATETIME      NOT NULL,
  scanned_loans      INT           NOT NULL DEFAULT 0,
  due_loans          INT           NOT NULL DEFAULT 0,
  settled_full       INT           NOT NULL DEFAULT 0,
  settled_partial    INT           NOT NULL DEFAULT 0,
  no_balance         INT           NOT NULL DEFAULT 0,
  balance_error      INT           NOT NULL DEFAULT 0,
  no_due             INT           NOT NULL DEFAULT 0,
  failed             INT           NOT NULL DEFAULT 0,
  amount_collected   DECIMAL(15,2) NOT NULL DEFAULT 0,
  error_summary      VARCHAR(1000) NULL,
  is_deleted         TINYINT(1)    NOT NULL DEFAULT 0,
  created_at         DATETIME      DEFAULT CURRENT_TIMESTAMP,
  updated_at         DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_repay_auto_audit_started (started_at),
  KEY idx_repay_auto_audit_source  (trigger_source)
);
