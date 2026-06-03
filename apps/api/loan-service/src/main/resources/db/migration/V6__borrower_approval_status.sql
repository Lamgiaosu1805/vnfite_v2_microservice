-- Thêm 2 trạng thái mới vào loan_requests:
--   AWAITING_BORROWER_APPROVAL: CMS đã thẩm định, đề nghị điều khoản, chờ khách xác nhận
--   CANCELLED: khách từ chối điều khoản hoặc tự rút hồ sơ

ALTER TABLE loan_requests
  MODIFY COLUMN status ENUM(
    'PENDING_REVIEW',
    'AWAITING_BORROWER_APPROVAL',
    'ACTIVE',
    'FUNDED',
    'REPAYING',
    'COMPLETED',
    'DEFAULTED',
    'REJECTED',
    'CANCELLED'
  ) NOT NULL DEFAULT 'PENDING_REVIEW';

-- Thêm cột lưu lý do từ chối của khách hàng (nếu cần ghi nhận sau)
ALTER TABLE loan_requests
  ADD COLUMN borrower_cancelled_reason VARCHAR(500) NULL AFTER rejection_reason;
