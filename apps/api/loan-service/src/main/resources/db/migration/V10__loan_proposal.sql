-- Phê duyệt 2 cấp: thẩm định viên (cấp 1) đề xuất số tiền + lãi suất → trình ban lãnh đạo (cấp 2)
-- duyệt (có thể sửa lãi suất). Trạng thái mới PENDING_APPROVAL nằm giữa PENDING_REVIEW và
-- AWAITING_BORROWER_APPROVAL (lưu dạng VARCHAR enum nên không cần đổi schema cho status).
ALTER TABLE loan_requests
    ADD COLUMN proposed_amount        DECIMAL(15,2) NULL AFTER interest_rate,
    ADD COLUMN proposed_interest_rate DECIMAL(5,2)  NULL AFTER proposed_amount,
    ADD COLUMN proposed_by            VARCHAR(100)  NULL AFTER proposed_interest_rate,
    ADD COLUMN proposed_at            DATETIME      NULL AFTER proposed_by,
    ADD COLUMN appraisal_note         VARCHAR(1000) NULL AFTER proposed_at;
