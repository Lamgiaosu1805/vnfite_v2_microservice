-- V11: mốc thời gian khoản gọi đủ vốn (FUNDED) — dùng để tính hết hạn ký khế ước.
ALTER TABLE loan_requests ADD COLUMN funded_at DATETIME NULL;

-- Backfill: cho các khoản FUNDED hiện có một mốc mới từ thời điểm deploy, tránh bị
-- scheduler hủy ngay lập tức (data-preserving, không reset dữ liệu nghiệp vụ).
UPDATE loan_requests
SET funded_at = NOW()
WHERE status = 'FUNDED' AND funded_at IS NULL;
