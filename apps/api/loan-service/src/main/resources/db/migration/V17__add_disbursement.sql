-- Thêm 2 trạng thái cho luồng ký hợp đồng + giải ngân:
--   AWAITING_DISBURSEMENT : người gọi vốn đã ký hợp đồng vay → vào hàng chờ giải ngân trên CMS.
--   DISBURSED             : OPS đã bấm "Giải ngân" → sinh lịch trả nợ từ ngày giải ngân.
ALTER TABLE loan_requests
  MODIFY COLUMN status ENUM(
    'PENDING_REVIEW',
    'PENDING_APPROVAL',
    'AWAITING_BORROWER_APPROVAL',
    'ACTIVE',
    'FUNDED',
    'AWAITING_DISBURSEMENT',
    'DISBURSED',
    'REPAYING',
    'COMPLETED',
    'DEFAULTED',
    'REJECTED',
    'CANCELLED'
  ) NOT NULL DEFAULT 'PENDING_REVIEW';

-- Thời điểm + người thực hiện giải ngân (OPS trên CMS).
ALTER TABLE loan_requests
  ADD COLUMN disbursed_at DATETIME    NULL AFTER reviewed_by,
  ADD COLUMN disbursed_by VARCHAR(100) NULL AFTER disbursed_at;
