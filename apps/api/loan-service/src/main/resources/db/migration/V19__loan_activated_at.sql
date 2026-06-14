-- V19: mốc thời gian khoản gọi vốn lên sàn (ACTIVE) — dùng để tính hết hạn gọi vốn.
ALTER TABLE loan_requests ADD COLUMN activated_at DATETIME NULL;

-- Backfill: cho các khoản ACTIVE hiện có một mốc mới từ thời điểm deploy, tránh bị
-- scheduler hết hạn ngay lập tức (data-preserving, không reset dữ liệu nghiệp vụ).
UPDATE loan_requests
SET activated_at = NOW()
WHERE status = 'ACTIVE' AND activated_at IS NULL;
