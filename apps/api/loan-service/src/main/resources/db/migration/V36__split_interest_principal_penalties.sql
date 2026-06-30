-- V36: Tách lãi/gốc đã trả + 2 loại phí phạt (lãi quá hạn, gốc quá hạn) + config tất toán sớm.
--
-- Mô hình tính tiền mới (chốt với ban lãnh đạo):
--   • Lãi flat trên gốc giải ngân ban đầu × lãi suất × số ngày thực tế / 365 (actual/365)
--   • Gốc chia đều mỗi kỳ; kỳ cuối gánh phần dư làm tròn
--   • Thứ tự hạch toán tiền trả: PHÍ → LÃI → GỐC
--   • Phí phạt lãi quá hạn  = lãi chưa trả  × 10%/năm        × ngày / 365  → nhà đầu tư (−TNCN)
--   • Phí phạt gốc quá hạn  = gốc chưa trả  × (150%×lãi suất) × ngày / 365 → nhà đầu tư (−TNCN)
--   • Phí tất toán trước hạn = 5% × gốc còn lại                            → VNFITE (doanh thu sàn)
--
-- Cột cũ giữ nguyên làm tổng (bất biến để mọi truy vấn/báo cáo hiện tại không vỡ):
--   paid_amount = interest_paid + principal_paid
--   late_fee      = interest_penalty      + principal_penalty
--   late_fee_paid = interest_penalty_paid + principal_penalty_paid

-- ── 1. repayment_schedule: tách phần đã trả lãi/gốc ───────────────────────────
ALTER TABLE repayment_schedule
    ADD COLUMN interest_paid  DECIMAL(15,2) NOT NULL DEFAULT 0 AFTER paid_amount,
    ADD COLUMN principal_paid DECIMAL(15,2) NOT NULL DEFAULT 0 AFTER interest_paid;

-- ── 2. repayment_schedule: tách 2 loại phí phạt ───────────────────────────────
ALTER TABLE repayment_schedule
    ADD COLUMN interest_penalty       DECIMAL(15,2) NOT NULL DEFAULT 0 AFTER late_fee_paid,
    ADD COLUMN interest_penalty_paid  DECIMAL(15,2) NOT NULL DEFAULT 0 AFTER interest_penalty,
    ADD COLUMN principal_penalty      DECIMAL(15,2) NOT NULL DEFAULT 0 AFTER interest_penalty_paid,
    ADD COLUMN principal_penalty_paid DECIMAL(15,2) NOT NULL DEFAULT 0 AFTER principal_penalty;

-- ── 3. Backfill các kỳ hiện có ────────────────────────────────────────────────
-- Lãi trả trước (interest-first) tái dựng từ paid_amount; phần còn lại là gốc.
UPDATE repayment_schedule
SET interest_paid  = LEAST(paid_amount, interest_due),
    principal_paid = paid_amount - LEAST(paid_amount, interest_due)
WHERE is_deleted = 0;

-- Phí phạt cũ tính trên dư nợ (gốc+lãi) gộp → quy về phí phạt gốc (xấp xỉ lịch sử, vô hại).
UPDATE repayment_schedule
SET principal_penalty      = late_fee,
    principal_penalty_paid = late_fee_paid
WHERE is_deleted = 0;

-- ── 4. loan_products: config phí mới ──────────────────────────────────────────
-- interest_penalty_rate: phí phạt lãi quá hạn (%/năm), mặc định 10
-- early_settlement_fee_rate: phí tất toán trước hạn (% gốc còn lại), mặc định 5
-- (late_fee_rate cũ = 150 giữ nguyên ý nghĩa = hệ số phạt GỐC quá hạn)
ALTER TABLE loan_products
    ADD COLUMN interest_penalty_rate     DECIMAL(5,2) NOT NULL DEFAULT 10.00 AFTER late_fee_rate,
    ADD COLUMN early_settlement_fee_rate DECIMAL(5,2) NOT NULL DEFAULT 5.00  AFTER interest_penalty_rate;

-- ── 5. Sổ tất toán trước hạn (ghi nhận phí 5% về VNFITE) ───────────────────────
CREATE TABLE early_settlement (
    id                 VARCHAR(36)  NOT NULL,
    loan_id            VARCHAR(36)  NOT NULL,
    loan_code          VARCHAR(50)  NULL,
    borrower_id        VARCHAR(36)  NULL,
    principal_settled  DECIMAL(15,2) NOT NULL,
    interest_to_date   DECIMAL(15,2) NOT NULL,
    penalty_paid       DECIMAL(15,2) NOT NULL DEFAULT 0,
    settlement_fee     DECIMAL(15,2) NOT NULL,
    settlement_fee_rate DECIMAL(5,2) NOT NULL,
    total_paid         DECIMAL(15,2) NOT NULL,
    settled_at         DATETIME     NOT NULL,
    settled_by         VARCHAR(100) NULL,
    is_deleted         TINYINT(1)   NOT NULL DEFAULT 0,
    created_at         DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_early_settlement_loan (loan_id),
    KEY idx_early_settlement_settled (settled_at),
    KEY idx_early_settlement_borrower (borrower_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
