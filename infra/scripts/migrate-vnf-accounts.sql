-- ============================================================
--  Migration: Import VNF accounts từ TIKLUY sang payment_db
-- ============================================================
-- Chạy script này KHI:
--   1. Chuyển từ VNFITE cũ sang VNFITE mới
--   2. Cả TIKLUY DB và payment_db cùng nằm trên 1 MySQL server
--
-- Trên test server (42.113.122.119):
--   TIKLUY DB = VNF_ACCOUNT_MANAGEMENT (trên cùng MySQL 42.113.122.119)
--   payment_db cũng trên cùng MySQL → chạy được cross-DB JOIN
--
-- Trên live server (42.113.122.118):
--   TIKLUY DB = VNF_ACCOUNT_MANAGEMENT trên 42.113.122.155 → khác server
--   Cần export từ 42.113.122.155 rồi import vào 42.113.122.118
--   (xem hướng dẫn export/import ở cuối file)
--
-- Cách chạy (test server — SSH vào 42.113.122.119):
--   source /root/p2p-lending/.env
--   mysql -u"${DB_USERNAME}" -p"${DB_PASSWORD}" -h 127.0.0.1 \
--     < /root/p2p-lending/infra/scripts/migrate-vnf-accounts.sql
--
-- Idempotent: chạy nhiều lần không bị trùng (ON DUPLICATE KEY / NOT EXISTS)
-- ============================================================

-- Bước 1: Import wallet từ TIKLUY vào payment_db
-- Link qua: auth_db.kyc_submissions.cccd_number = VNF_ACCOUNT_MANAGEMENT.tbl_account_information.IDENTITY_NUMBER
INSERT INTO payment_db.wallets (
    id,
    user_id,
    vnf_account_no,
    total_balance,
    locked_balance,
    is_deleted,
    created_at,
    updated_at
)
SELECT
    UUID()                          AS id,
    ks.user_id                      AS user_id,
    ta.ACC_NO                       AS vnf_account_no,
    COALESCE(ta.TOTAL_MONEY,  0)    AS total_balance,
    COALESCE(ta.LOCKED_MONEY, 0)    AS locked_balance,
    0                               AS is_deleted,
    NOW()                           AS created_at,
    NOW()                           AS updated_at
FROM auth_db.kyc_submissions ks
JOIN VNF_ACCOUNT_MANAGEMENT.tbl_account_information ta
    ON ta.IDENTITY_NUMBER = ks.cccd_number
WHERE ta.IS_DELETE  = 'N'
  AND ta.SOURCE     = 'VNFITE'      -- chỉ lấy tài khoản của VNFITE, không lấy TIKLUY CAPITAL (VNC)
  AND ks.status     = 'APPROVED'
  AND ks.is_deleted = 0
  -- Chỉ insert user chưa có wallet
  AND NOT EXISTS (
    SELECT 1 FROM payment_db.wallets w
    WHERE w.user_id    = ks.user_id
      AND w.is_deleted = 0
  );

-- Bước 2: Kiểm tra kết quả
SELECT
    w.user_id,
    w.vnf_account_no,
    w.total_balance,
    w.locked_balance,
    w.created_at,
    ta.ACC_NAME        AS tikluy_acc_name
FROM payment_db.wallets w
JOIN VNF_ACCOUNT_MANAGEMENT.tbl_account_information ta
    ON ta.ACC_NO = w.vnf_account_no
ORDER BY w.created_at DESC
LIMIT 50;

-- ============================================================
--  Trường hợp live: TIKLUY DB ở server khác (42.113.122.155)
-- ============================================================
-- Bước A — Chạy trên 42.113.122.155, export accounts:
--
--   mysql -u vnfite_4 -p'Vnfite20240712!@#' -h 127.0.0.1 VNF_ACCOUNT_MANAGEMENT \
--     -e "SELECT IDENTITY_NUMBER, ACC_NO, TOTAL_MONEY, LOCKED_MONEY
--         FROM tbl_account_information
--         WHERE IS_DELETE='N' AND SOURCE='VNFITE'" \
--     --batch --silent > /tmp/vnf_accounts.tsv
--
-- Bước B — Copy sang 42.113.122.118:
--   scp root@42.113.122.155:/tmp/vnf_accounts.tsv /tmp/
--
-- Bước C — Chạy import thủ công hoặc dùng endpoint:
--   POST /internal/payment/wallet/link-existing
--   Header: X-Internal-Secret: <secret>
--   Params: userId=<uid>&vnfAccountNo=VNF0000000001
-- ============================================================
