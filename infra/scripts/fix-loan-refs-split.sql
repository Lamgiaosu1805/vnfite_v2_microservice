-- =============================================================================
-- Fix: tách ref1/ref2 người tham chiếu bị gộp bằng " - " từ hệ thống cũ
--
-- Hệ thống cũ lưu cả 2 người tham chiếu vào 1 trường, cách nhau bằng " - ".
-- Script migration đã đưa toàn bộ vào ref1_*, bỏ sót ref2_*.
-- Script này split dữ liệu đó ra đúng chỗ.
--
-- Chạy trên test server (119):
--   source /root/p2p-lending/.env && \
--   mysql -u"${DB_USERNAME}" -p"${DB_PASSWORD}" -h 127.0.0.1 loan_db < \
--     /root/p2p-lending/infra/scripts/fix-loan-refs-split.sql
--
-- Chạy trên live server (118):
--   source /root/p2p-lending/.env && \
--   mysql -u"${DB_USERNAME}" -p"${DB_PASSWORD}" -h 127.0.0.1 loan_db < \
--     /root/p2p-lending/infra/scripts/fix-loan-refs-split.sql
--
-- Idempotent: WHERE condition đảm bảo chỉ chạy trên row chưa được tách.
-- =============================================================================

UPDATE loan_requests
SET
  -- ref2: lấy phần SAU dấu " - " đầu tiên trong từng trường ref1
  ref2_full_name    = CASE
    WHEN ref1_full_name    LIKE '% - %'
    THEN NULLIF(TRIM(SUBSTRING_INDEX(ref1_full_name,    ' - ', -1)), '')
    ELSE ref2_full_name
  END,
  ref2_relationship = CASE
    WHEN ref1_relationship LIKE '% - %'
    THEN NULLIF(TRIM(SUBSTRING_INDEX(ref1_relationship, ' - ', -1)), '')
    ELSE ref2_relationship
  END,
  ref2_phone        = CASE
    WHEN ref1_phone        LIKE '% - %'
    THEN LEFT(NULLIF(TRIM(SUBSTRING_INDEX(ref1_phone,   ' - ', -1)), ''), 20)
    ELSE ref2_phone
  END,
  ref2_address      = CASE
    WHEN ref1_address      LIKE '% - %'
    THEN NULLIF(TRIM(SUBSTRING_INDEX(ref1_address,      ' - ', -1)), '')
    ELSE ref2_address
  END,

  -- ref1: cắt lại, chỉ giữ phần TRƯỚC dấu " - " đầu tiên
  ref1_full_name    = CASE
    WHEN ref1_full_name    LIKE '% - %'
    THEN NULLIF(TRIM(SUBSTRING_INDEX(ref1_full_name,    ' - ', 1)), '')
    ELSE ref1_full_name
  END,
  ref1_relationship = CASE
    WHEN ref1_relationship LIKE '% - %'
    THEN NULLIF(TRIM(SUBSTRING_INDEX(ref1_relationship, ' - ', 1)), '')
    ELSE ref1_relationship
  END,
  ref1_phone        = CASE
    WHEN ref1_phone        LIKE '% - %'
    THEN LEFT(NULLIF(TRIM(SUBSTRING_INDEX(ref1_phone,   ' - ', 1)), ''), 20)
    ELSE ref1_phone
  END,
  ref1_address      = CASE
    WHEN ref1_address      LIKE '% - %'
    THEN NULLIF(TRIM(SUBSTRING_INDEX(ref1_address,      ' - ', 1)), '')
    ELSE ref1_address
  END

WHERE
  -- Chỉ xử lý row có ít nhất một trường ref1 bị gộp
  (
    ref1_full_name    LIKE '% - %'
    OR ref1_relationship LIKE '% - %'
    OR ref1_phone        LIKE '% - %'
    OR ref1_address      LIKE '% - %'
  )
  -- Và ref2_full_name chưa có (tránh ghi đè nếu đã chạy)
  AND (ref2_full_name IS NULL OR ref2_full_name = '');
