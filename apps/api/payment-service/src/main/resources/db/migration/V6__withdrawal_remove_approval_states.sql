-- Loại bỏ các trạng thái manual-approval không còn dùng (PENDING_APPROVAL, APPROVED, REJECTED).
-- Luồng rút tiền chuyển sang tự động hoàn toàn sau khi user xác nhận OTP.

ALTER TABLE withdrawal_requests
    MODIFY COLUMN status ENUM(
        'INITIATED',
        'OTP_PENDING',
        'FUNDS_LOCKED',
        'TRANSFER_INITIATED',
        'PROCESSING',
        'COMPLETED',
        'TRANSFER_FAILED',
        'FAILED',
        'CANCELLED',
        'FUNDS_RELEASED'
    ) NOT NULL DEFAULT 'INITIATED';

-- Thêm non_retryable_error_codes vào config để ops tự cấu hình lỗi nào không nên retry.
-- Danh sách phân tách bởi dấu phẩy, ví dụ: 'INSUFFICIENT_FUNDS,ACCOUNT_LOCKED,INVALID_ACCOUNT'
ALTER TABLE withdrawal_transfer_configs
    ADD COLUMN non_retryable_error_codes VARCHAR(500) NOT NULL DEFAULT ''
        COMMENT 'Comma-separated error codes that should NOT be retried (e.g. INSUFFICIENT_FUNDS,ACCOUNT_LOCKED)';

-- Nâng limits lên mức rất cao (gần như không giới hạn) — ops chỉnh DB khi cần siết.
-- max_per_txn: 500M → không đổi (hợp lý per-transaction)
-- max_daily_total: 2.5B → 100B (không giới hạn thực tế)
-- max_daily_count: 5 → 999 (không giới hạn thực tế)
-- non_retryable_error_codes: để trống — điền sau khi thấy error code thật từ MB trong log.
--   Cách điền: UPDATE withdrawal_transfer_configs SET non_retryable_error_codes = 'CODE1,CODE2' WHERE active = 1;
UPDATE withdrawal_transfer_configs
SET max_daily_total = 100000000000,
    max_daily_count = 999,
    non_retryable_error_codes = ''
WHERE active = 1 AND is_deleted = 0;
