#!/bin/bash
# =============================================================================
# Full migration: APP_V2 (hệ thống cũ) → auth_db + payment_db (hệ thống mới)
# Chạy trực tiếp trên server 118:
#   bash /root/p2p-lending/infra/scripts/migrate-all-users.sh
#
# Script idempotent — chạy nhiều lần không bị lỗi (ON DUPLICATE KEY UPDATE).
# Không xóa hay reset dữ liệu đã có.
#
# Xử lý số điện thoại trùng:
#   Với mỗi nhóm cùng USER_NAME (phone), chỉ lấy user có giao dịch gần nhất.
#   Nếu không có giao dịch nào thì lấy user có CREATED_DATE muộn nhất.
# =============================================================================

set -euo pipefail

# ── Credentials ──────────────────────────────────────────────────────────────
DB_HOST="127.0.0.1"
DB_USER="vnfite"
DB_PASS="Vnfite080620!@#"

TIKLUY_HOST="42.113.122.155"
TIKLUY_USER="vnfite_4"
TIKLUY_PASS="Vnfite20240712!@#"

MYSQL_OLD="mysql -u${DB_USER} -p${DB_PASS} -h${DB_HOST} --default-character-set=utf8mb4 -s"
MYSQL_155="mysql -u${TIKLUY_USER} -p${TIKLUY_PASS} -h${TIKLUY_HOST} --default-character-set=utf8mb4 -s"

log() { echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*"; }

# ── Step 1: Tạo bảng tạm lưu mapping CCCD → vnf_account_no từ server 155 ───
log "Step 1: Tạo bảng tạm _tmp_vnf_acc trong APP_V2..."
$MYSQL_OLD APP_V2 <<'ENDSQL' 2>/dev/null
CREATE TABLE IF NOT EXISTS _tmp_vnf_acc (
  cccd    VARCHAR(100) NOT NULL,
  acc_no  VARCHAR(50)  NOT NULL,
  PRIMARY KEY (cccd)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
TRUNCATE TABLE _tmp_vnf_acc;
ENDSQL

log "Step 1b: Load VNF acc_no từ server 155..."
if $MYSQL_155 VNF_ACCOUNT_MANAGEMENT --connect-timeout=10 -e "SELECT 1;" 2>/dev/null; then
  $MYSQL_155 VNF_ACCOUNT_MANAGEMENT --skip-column-names -e \
    "SELECT CONCAT('INSERT IGNORE INTO _tmp_vnf_acc VALUES (', QUOTE(IDENTITY_NUMBER), ',', QUOTE(acc_no), ');')
     FROM tbl_account_information
     WHERE acc_no LIKE 'VNF%'
       AND IDENTITY_NUMBER IS NOT NULL
       AND IDENTITY_NUMBER != ''
       AND (is_delete IS NULL OR is_delete = 'N')" 2>/dev/null \
  | $MYSQL_OLD APP_V2 2>/dev/null
  log "  → Kết nối server 155 thành công, đã load VNF accounts."
else
  log "  ⚠️  Không kết nối được server 155 — bỏ qua VNF account mapping (ví sẽ không được tạo)."
fi

MAPPED=$(${MYSQL_OLD} APP_V2 --skip-column-names -e "SELECT COUNT(*) FROM _tmp_vnf_acc;" 2>/dev/null)
log "  → Đã load ${MAPPED} VNF accounts."

# ── Step 1c: Tạo bảng dedup — 1 user_id duy nhất cho mỗi phone ──────────────
# Tiêu chí chọn (ưu tiên giảm dần):
#   1. User có giao dịch gần nhất (MAX tbl_transaction.CREATED_DATE)
#   2. Nếu không có giao dịch: dùng CREATED_DATE muộn nhất của user
log "Step 1c: Dedup users theo số điện thoại (giữ account hoạt động gần nhất)..."
$MYSQL_OLD APP_V2 <<'ENDSQL' 2>/dev/null
DROP TABLE IF EXISTS _tmp_best_user;
CREATE TABLE _tmp_best_user (
  user_id    VARCHAR(50)  NOT NULL,
  phone      VARCHAR(20)  NOT NULL,
  PRIMARY KEY (user_id),
  UNIQUE KEY uq_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO _tmp_best_user (user_id, phone)
SELECT best.user_id, best.phone
FROM (
  SELECT
    u.ID   AS user_id,
    u.USER_NAME AS phone,
    -- Sắp xếp: giao dịch gần nhất → CREATED_DATE muộn nhất
    ROW_NUMBER() OVER (
      PARTITION BY u.USER_NAME
      ORDER BY
        COALESCE(
          (SELECT MAX(t.CREATED_DATE) FROM tbl_transaction t WHERE t.USER_ID = u.ID),
          u.CREATED_DATE
        ) DESC,
        u.ID DESC  -- tiebreak nếu cùng thời điểm
    ) AS rn
  FROM tbl_user u
  WHERE u.IS_DELETED = 'N'
    AND u.USER_NAME IS NOT NULL
    AND u.USER_NAME != ''
) best
WHERE best.rn = 1;
ENDSQL

TOTAL_RAW=$(${MYSQL_OLD} APP_V2 --skip-column-names -e "SELECT COUNT(*) FROM tbl_user WHERE IS_DELETED='N' AND USER_NAME IS NOT NULL AND USER_NAME != '';" 2>/dev/null)
TOTAL_DEDUP=$(${MYSQL_OLD} APP_V2 --skip-column-names -e "SELECT COUNT(*) FROM _tmp_best_user;" 2>/dev/null)
DUPES=$(( TOTAL_RAW - TOTAL_DEDUP ))
log "  → Tổng user gốc: ${TOTAL_RAW} | Sau dedup: ${TOTAL_DEDUP} | Đã loại bỏ: ${DUPES} duplicate(s)"

# ── Step 2: Migrate auth_db.users ────────────────────────────────────────────
log "Step 2: Migrate users → auth_db.users (chỉ user đã dedup)..."
$MYSQL_OLD APP_V2 <<'ENDSQL'
INSERT INTO auth_db.users
  (id, phone, password, email, kyc_status, referred_by, is_deleted, created_at, updated_at)
SELECT
  b.user_id,
  b.phone,
  u.PASSWORD,
  NULLIF(TRIM(COALESCE(i.EMAIL, '')), ''),
  CASE
    WHEN i.ID IS NULL THEN 'NONE'
    WHEN i.STATUS = 1 THEN 'APPROVED'
    WHEN i.STATUS = 0 THEN 'PENDING'
    ELSE 'NONE'
  END AS kyc_status,
  NULL AS referred_by,
  0    AS is_deleted,
  u.CREATED_DATE,
  NOW()
FROM _tmp_best_user b
JOIN tbl_user u ON u.ID = b.user_id
LEFT JOIN tbl_identification_info i
  ON i.ID = (
    SELECT MAX(x.ID)
    FROM tbl_identification_info x
    WHERE x.USER_ID = b.user_id AND x.IS_DELETED = 'N'
  )
ON DUPLICATE KEY UPDATE
  email      = VALUES(email),
  kyc_status = VALUES(kyc_status),
  updated_at = NOW();
ENDSQL
USERS=$(${MYSQL_OLD} APP_V2 --skip-column-names -e "SELECT COUNT(*) FROM auth_db.users;" 2>/dev/null)
log "  → auth_db.users tổng: ${USERS}"

# ── Step 3: Migrate auth_db.kyc_submissions ───────────────────────────────────
log "Step 3: Migrate KYC → auth_db.kyc_submissions (chỉ user đã dedup)..."
$MYSQL_OLD APP_V2 <<'ENDSQL' 2>/dev/null
INSERT INTO auth_db.kyc_submissions
  (id, user_id, cccd_number, full_name, gender,
   date_of_birth, permanent_address, hometown,
   issue_date, issuing_authority, expiry_date,
   front_image_id, back_image_id, portrait_image_id,
   status, is_deleted, created_at, updated_at)
SELECT
  i.ID,
  i.USER_ID,
  i.LEGAL_ID,
  COALESCE(i.FULL_NAME, ''),
  CASE WHEN i.GENDER = 1 THEN 'MALE' ELSE 'FEMALE' END,
  i.BIRTHDAY,
  COALESCE(i.PERMANENT_ADDRESS, ''),
  COALESCE(i.DOMICILE, i.RESIDENT, ''),
  i.LEGAL_ISSUE_DATE,
  COALESCE(i.LEGAL_PLACE, ''),
  i.LEGAL_EXP_DATE,
  COALESCE(i.FRONT_IMG_PATH, 'legacy_no_image'),
  COALESCE(i.BACK_IMG_PATH,  'legacy_no_image'),
  COALESCE(i.PORTRAIT,       'legacy_no_image'),
  CASE WHEN i.STATUS = 1 THEN 'APPROVED' ELSE 'PENDING' END,
  0,
  i.CREATED_DATE,
  NOW()
FROM tbl_identification_info i
JOIN _tmp_best_user b ON b.user_id = i.USER_ID  -- chỉ lấy KYC của user đã chọn
WHERE i.IS_DELETED = 'N'
  AND i.LEGAL_ID   IS NOT NULL
  AND i.LEGAL_ID   != ''
  AND i.BIRTHDAY   IS NOT NULL
  AND i.LEGAL_ISSUE_DATE IS NOT NULL
ON DUPLICATE KEY UPDATE
  status     = VALUES(status),
  full_name  = VALUES(full_name),
  updated_at = NOW();
ENDSQL
KYC=$(${MYSQL_OLD} APP_V2 --skip-column-names -e "SELECT COUNT(*) FROM auth_db.kyc_submissions;" 2>/dev/null)
log "  → auth_db.kyc_submissions tổng: ${KYC}"

# ── Step 4: Migrate payment_db.wallets ───────────────────────────────────────
log "Step 4: Migrate wallets → payment_db.wallets (chỉ user đã dedup)..."
$MYSQL_OLD APP_V2 <<'ENDSQL' 2>/dev/null
INSERT INTO payment_db.wallets
  (id, user_id, vnf_account_no, locked_balance, is_deleted, created_at, updated_at)
SELECT
  i.ID,
  b.user_id,
  a.acc_no,
  0.00,
  0,
  u.CREATED_DATE,
  NOW()
FROM _tmp_best_user b
JOIN tbl_user u ON u.ID = b.user_id
JOIN tbl_identification_info i ON i.USER_ID = b.user_id AND i.IS_DELETED = 'N'
JOIN _tmp_vnf_acc a ON a.cccd = i.LEGAL_ID
WHERE i.LEGAL_ID IS NOT NULL
ON DUPLICATE KEY UPDATE
  updated_at = NOW();
ENDSQL
WALLETS=$(${MYSQL_OLD} APP_V2 --skip-column-names -e "SELECT COUNT(*) FROM payment_db.wallets;" 2>/dev/null)
log "  → payment_db.wallets tổng: ${WALLETS}"

# ── Step 5: Migrate payment_db.linked_banks ───────────────────────────────────
log "Step 5: Migrate ngân hàng liên kết → payment_db.linked_banks (chỉ user đã dedup)..."
$MYSQL_OLD APP_V2 <<'ENDSQL' 2>/dev/null
INSERT INTO payment_db.linked_banks
  (id, user_id, bank_code, bank_name, bank_account_no, account_name,
   is_default, is_deleted, created_at, updated_at)
SELECT
  b_info.ID,
  b_info.USER_ID,
  b_info.BANK_CODE,
  b_info.BANK_CODE,
  b_info.BANK_ACCOUNT_NUMBER,
  b_info.BANK_ACCOUNT_NAME,
  1,
  0,
  b_info.CREATED_DATE,
  NOW()
FROM tbl_associate_bank_information b_info
JOIN _tmp_best_user b ON b.user_id = b_info.USER_ID  -- chỉ lấy ngân hàng của user đã chọn
WHERE b_info.IS_DELETED = 'N'
ON DUPLICATE KEY UPDATE
  updated_at = NOW();
ENDSQL
BANKS=$(${MYSQL_OLD} APP_V2 --skip-column-names -e "SELECT COUNT(*) FROM payment_db.linked_banks;" 2>/dev/null)
log "  → payment_db.linked_banks tổng: ${BANKS}"

# ── Step 6: Migrate payment_db.wallet_transactions ───────────────────────────
log "Step 6: Migrate giao dịch → payment_db.wallet_transactions (chỉ user đã dedup)..."
$MYSQL_OLD APP_V2 <<'ENDSQL' 2>/dev/null
INSERT INTO payment_db.wallet_transactions
  (id, wallet_id, type, amount, status,
   reference_id, external_ref, description, balance_after,
   is_deleted, created_at, updated_at)
SELECT
  t.ID,
  w.id,
  CASE WHEN t.CATEGORY = 0 THEN 'DEPOSIT' ELSE 'WITHDRAW' END,
  CAST(t.AMOUNT AS DECIMAL(15,2)),
  CASE
    WHEN t.STATUS = 0 THEN 'PENDING'
    WHEN t.STATUS = 1 THEN 'SUCCESS'
    WHEN t.STATUS = 2 THEN 'FAILED'
    ELSE 'PENDING'
  END,
  NULL,
  NULL,
  t.DETAILS,
  NULL,
  CASE WHEN t.IS_DELETED = 'Y' THEN 1 ELSE 0 END,
  t.CREATED_DATE,
  COALESCE(t.UPDATED_DATE, t.CREATED_DATE)
FROM tbl_transaction t
JOIN _tmp_best_user b ON b.user_id = t.USER_ID  -- chỉ lấy giao dịch của user đã chọn
JOIN payment_db.wallets w ON w.user_id = t.USER_ID
ON DUPLICATE KEY UPDATE
  updated_at = NOW();
ENDSQL
TXN=$(${MYSQL_OLD} APP_V2 --skip-column-names -e "SELECT COUNT(*) FROM payment_db.wallet_transactions;" 2>/dev/null)
log "  → payment_db.wallet_transactions tổng: ${TXN}"

# ── Step 7: Cập nhật số dư ví từ giao dịch đã migrate ───────────────────────
log "Step 7: Tính lại total_balance cho từng ví từ giao dịch..."
$MYSQL_OLD APP_V2 <<'ENDSQL' 2>/dev/null
UPDATE payment_db.wallets w
JOIN (
  SELECT
    wt.wallet_id,
    SUM(
      CASE
        WHEN wt.type = 'DEPOSIT'  AND wt.status = 'SUCCESS' THEN  wt.amount
        WHEN wt.type = 'WITHDRAW' AND wt.status = 'SUCCESS' THEN -wt.amount
        ELSE 0
      END
    ) AS net_balance
  FROM payment_db.wallet_transactions wt
  GROUP BY wt.wallet_id
) calc ON calc.wallet_id = w.id
SET
  w.total_balance     = GREATEST(calc.net_balance, 0),
  w.available_balance = GREATEST(calc.net_balance, 0) - w.locked_balance,
  w.updated_at        = NOW()
WHERE EXISTS (SELECT 1 FROM _tmp_best_user b WHERE b.user_id = w.user_id);
ENDSQL
log "  → Cập nhật số dư ví hoàn tất."

# ── Step 8: Dọn bảng tạm ─────────────────────────────────────────────────────
log "Step 8: Drop bảng tạm..."
$MYSQL_OLD APP_V2 -e "DROP TABLE IF EXISTS _tmp_vnf_acc; DROP TABLE IF EXISTS _tmp_best_user;" 2>/dev/null

log "===== Migration hoàn thành ====="
log "Users:        ${USERS:-0} (từ ${TOTAL_RAW:-0} gốc, loại ${DUPES:-0} duplicate)"
log "KYC:          ${KYC:-0}"
log "Wallets:      ${WALLETS:-0}"
log "Linked banks: ${BANKS:-0}"
log "Transactions: ${TXN:-0}"
