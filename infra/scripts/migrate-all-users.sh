#!/bin/bash
# =============================================================================
# Full migration: APP_V2 (hệ thống cũ) → auth_db + payment_db (hệ thống mới)
# Chạy trực tiếp trên server 118:
#   bash /root/p2p-lending/infra/scripts/migrate-all-users.sh
#
# Script idempotent — chạy nhiều lần không bị lỗi (ON DUPLICATE KEY UPDATE).
# Không xóa hay reset dữ liệu đã có.
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
$MYSQL_155 VNF_ACCOUNT_MANAGEMENT --skip-column-names -e \
  "SELECT CONCAT('INSERT IGNORE INTO _tmp_vnf_acc VALUES (', QUOTE(IDENTITY_NUMBER), ',', QUOTE(acc_no), ');')
   FROM tbl_account_information
   WHERE acc_no LIKE 'VNF%'
     AND IDENTITY_NUMBER IS NOT NULL
     AND IDENTITY_NUMBER != ''
     AND (is_delete IS NULL OR is_delete = 'N')" 2>/dev/null \
| $MYSQL_OLD APP_V2 2>/dev/null

MAPPED=$(${MYSQL_OLD} APP_V2 --skip-column-names -e "SELECT COUNT(*) FROM _tmp_vnf_acc;" 2>/dev/null)
log "  → Đã load ${MAPPED} VNF accounts."

# ── Step 2: Migrate auth_db.users ────────────────────────────────────────────
log "Step 2: Migrate users → auth_db.users..."
$MYSQL_OLD APP_V2 <<'ENDSQL' 2>/dev/null
INSERT INTO auth_db.users
  (id, phone, password, email, kyc_status, referred_by, is_deleted, created_at, updated_at)
SELECT
  u.ID,
  u.USER_NAME,
  u.PASSWORD,
  i.EMAIL,
  CASE
    WHEN i.ID IS NULL    THEN 'NONE'
    WHEN i.STATUS = 0    THEN 'PENDING'
    WHEN i.STATUS = 1    THEN 'APPROVED'
    ELSE 'NONE'
  END AS kyc_status,
  NULL AS referred_by,
  0    AS is_deleted,
  u.CREATED_DATE,
  NOW()
FROM tbl_user u
LEFT JOIN (
  SELECT USER_ID, EMAIL, STATUS, ID
  FROM tbl_identification_info
  WHERE IS_DELETED = 'N'
) i ON i.USER_ID = u.ID
WHERE u.IS_DELETED = 'N'
ON DUPLICATE KEY UPDATE
  email      = VALUES(email),
  kyc_status = VALUES(kyc_status),
  updated_at = NOW();
ENDSQL
USERS=$(${MYSQL_OLD} APP_V2 --skip-column-names -e "SELECT COUNT(*) FROM auth_db.users;" 2>/dev/null)
log "  → auth_db.users tổng: ${USERS}"

# ── Step 3: Migrate auth_db.kyc_submissions ───────────────────────────────────
log "Step 3: Migrate KYC → auth_db.kyc_submissions..."
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
WHERE i.IS_DELETED = 'N'
  AND i.LEGAL_ID   IS NOT NULL
  AND i.LEGAL_ID   != ''
  AND i.BIRTHDAY   IS NOT NULL
  AND i.LEGAL_ISSUE_DATE IS NOT NULL
  AND EXISTS (SELECT 1 FROM auth_db.users u WHERE u.id = i.USER_ID)
ON DUPLICATE KEY UPDATE
  status     = VALUES(status),
  updated_at = NOW();
ENDSQL
KYC=$(${MYSQL_OLD} APP_V2 --skip-column-names -e "SELECT COUNT(*) FROM auth_db.kyc_submissions;" 2>/dev/null)
log "  → auth_db.kyc_submissions tổng: ${KYC}"

# ── Step 4: Migrate payment_db.wallets ───────────────────────────────────────
log "Step 4: Migrate wallets → payment_db.wallets..."
$MYSQL_OLD APP_V2 <<'ENDSQL' 2>/dev/null
INSERT INTO payment_db.wallets
  (id, user_id, vnf_account_no, locked_balance, is_deleted, created_at, updated_at)
SELECT
  i.ID,
  u.ID,
  a.acc_no,
  0.00,
  0,
  u.CREATED_DATE,
  NOW()
FROM tbl_user u
JOIN tbl_identification_info i ON i.USER_ID = u.ID AND i.IS_DELETED = 'N'
JOIN _tmp_vnf_acc a ON a.cccd = i.LEGAL_ID
WHERE u.IS_DELETED = 'N'
  AND i.LEGAL_ID IS NOT NULL
ON DUPLICATE KEY UPDATE
  updated_at = NOW();
ENDSQL
WALLETS=$(${MYSQL_OLD} APP_V2 --skip-column-names -e "SELECT COUNT(*) FROM payment_db.wallets;" 2>/dev/null)
log "  → payment_db.wallets tổng: ${WALLETS}"

# ── Step 5: Migrate payment_db.linked_banks ───────────────────────────────────
log "Step 5: Migrate ngân hàng liên kết → payment_db.linked_banks..."
$MYSQL_OLD APP_V2 <<'ENDSQL' 2>/dev/null
INSERT INTO payment_db.linked_banks
  (id, user_id, bank_code, bank_name, bank_account_no, account_name,
   is_default, is_deleted, created_at, updated_at)
SELECT
  b.ID,
  b.USER_ID,
  b.BANK_CODE,
  b.BANK_CODE,
  b.BANK_ACCOUNT_NUMBER,
  b.BANK_ACCOUNT_NAME,
  1,
  0,
  b.CREATED_DATE,
  NOW()
FROM tbl_associate_bank_information b
WHERE b.IS_DELETED = 'N'
  AND EXISTS (SELECT 1 FROM auth_db.users u WHERE u.id = b.USER_ID)
ON DUPLICATE KEY UPDATE
  updated_at = NOW();
ENDSQL
BANKS=$(${MYSQL_OLD} APP_V2 --skip-column-names -e "SELECT COUNT(*) FROM payment_db.linked_banks;" 2>/dev/null)
log "  → payment_db.linked_banks tổng: ${BANKS}"

# ── Step 6: Migrate payment_db.wallet_transactions ───────────────────────────
log "Step 6: Migrate giao dịch → payment_db.wallet_transactions..."
$MYSQL_OLD APP_V2 <<'ENDSQL' 2>/dev/null
INSERT INTO payment_db.wallet_transactions
  (id, wallet_id, type, amount, status,
   reference_id, external_ref, description, balance_after,
   is_deleted, created_at, updated_at)
SELECT
  t.ID,
  w.id,
  CASE WHEN t.CATEGORY = 0 THEN 'DEPOSIT' ELSE 'WITHDRAWAL' END,
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
JOIN tbl_identification_info i ON i.USER_ID = t.USER_ID AND i.IS_DELETED = 'N'
JOIN payment_db.wallets w ON w.user_id = t.USER_ID
ON DUPLICATE KEY UPDATE
  updated_at = NOW();
ENDSQL
TXN=$(${MYSQL_OLD} APP_V2 --skip-column-names -e "SELECT COUNT(*) FROM payment_db.wallet_transactions;" 2>/dev/null)
log "  → payment_db.wallet_transactions tổng: ${TXN}"

# ── Step 7: Dọn bảng tạm ─────────────────────────────────────────────────────
log "Step 7: Drop bảng tạm _tmp_vnf_acc..."
$MYSQL_OLD APP_V2 -e "DROP TABLE IF EXISTS _tmp_vnf_acc;" 2>/dev/null

log "===== Migration hoàn thành ====="
log "Users:        ${USERS}"
log "KYC:          ${KYC}"
log "Wallets:      ${WALLETS}"
log "Linked banks: ${BANKS}"
log "Transactions: ${TXN}"
