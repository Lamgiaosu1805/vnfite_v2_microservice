#!/bin/bash
# =============================================================================
# Migrate loans: APP_V2 (hệ thống cũ) → loan_db (hệ thống mới)
# Chạy trực tiếp trên server 118:
#   bash /root/p2p-lending/infra/scripts/migrate-loans.sh
#
# Idempotent — chạy nhiều lần không bị lỗi (ON DUPLICATE KEY UPDATE).
#
# Source enums (từ Constant.java hệ thống cũ):
#   LOAN_STATUS:        0=BORROWING | 1=BORROWED | 2=CANCELLED
#   LOAN_STATUS_DETAIL: 0=ECONTRACT | 1=WAITING_FOR_INVESTMENT | 2=NOT_ELIGIBLE
#                       3=DISBURSED | 4=OVERDUE | 5=END_FUNDING_PERIOD
#                       6=WAITING_FOR_DISBURSEMENT | 7=DISBURSEMENT_CONFIRMATION
#   PAYMENT_STATUS:     0=UNPAID | 1=PAID | 2=OVERDUE
#
# Mapping tbl_user_loan → loan_requests.status:
#   NULL / NULL         → PENDING_REVIEW  (chưa xử lý)
#   NULL / 0 ECONTRACT  → PENDING_REVIEW  (chờ ký hợp đồng điện tử)
#   NULL / 1 WAITING    → ACTIVE          (đang gọi vốn)
#   NULL / 2 NOT_ELIG.  → REJECTED        (không đủ điều kiện)
#   NULL / 6 W.DISBURS. → AWAITING_DISBURSEMENT (chờ xác nhận giải ngân)
#   NULL / 7 DISBURS.   → DISBURSED       (đã xác nhận giải ngân)
#   0    / 3 DISBURSED  → REPAYING        (BORROWING + đã giải ngân, đang trả nợ)
#   1    / 3 DISBURSED  → COMPLETED       (BORROWED + đã giải ngân, đã trả xong)
#   2    / 0 ECONTRACT  → CANCELLED       (hủy trong giai đoạn ký HĐ)
#   2    / 2 NOT_ELIG.  → REJECTED        (bị từ chối, không đủ điều kiện)
#
#   tbl_payment_period.STATUS → repayment_schedule.status:
#   0=UNPAID → PENDING | 1=PAID → PAID | 2=OVERDUE → OVERDUE
# =============================================================================

set -euo pipefail

# Đọc credentials từ .env nếu có (test server), fallback về giá trị cứng (live server)
ENV_FILE="/root/p2p-lending/.env"
if [[ -f "$ENV_FILE" ]]; then
  set -a; source "$ENV_FILE"; set +a
fi
DB_HOST="${MYSQL_HOST:-127.0.0.1}"
DB_USER="${DB_USERNAME:-vnfite}"
DB_PASS="${DB_PASSWORD:-Vnfite080620!@#}"

MYSQL="mysql -u${DB_USER} -p${DB_PASS} -h${DB_HOST} --default-character-set=utf8mb4 -s"

log() { echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*"; }

# Sản phẩm phải được migrate trước để mọi loan giữ đúng quan hệ product_id.
# Script sản phẩm idempotent và đồng thời backfill product_id cho các loan cũ.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
log "Step 0: Đồng bộ sản phẩm gọi vốn trước khi migrate khoản gọi vốn..."
bash "${SCRIPT_DIR}/migrate-products.sh"

# ── Step 1: Migrate loan_db.loan_requests ────────────────────────────────────
log "Step 1: Migrate tbl_user_loan → loan_db.loan_requests..."
$MYSQL APP_V2 <<'ENDSQL'
INSERT INTO loan_db.loan_requests
  (id, borrower_id, product_id, amount, interest_rate, term_months, purpose,
   status, funded_amount,
   monthly_income, occupation, current_address,
   ref1_full_name, ref1_relationship, ref1_phone,
   ref2_full_name, ref2_relationship, ref2_phone,
   loan_seq,
   is_deleted, created_at, updated_at)
SELECT
  l.ID,
  l.USER_ID,
  NULLIF(TRIM(l.LOAN_PRODUCT_PACKAGE_ID), ''),
  CAST(NULLIF(TRIM(l.AMOUNT), '') AS DECIMAL(15,2)),
  CAST(NULLIF(TRIM(l.LOAN_INTEREST_RATE), '') AS DECIMAL(5,2)),
  l.PERIOD,
  COALESCE(NULLIF(TRIM(p.NAME), ''), 'Khác'),
  -- Status mapping (dựa theo LOAN_STATUS + LOAN_STATUS_DETAIL từ Constant.java cũ)
  CASE
    WHEN l.STATUS = 1 AND l.STATUS_DETAIL = 3      THEN 'COMPLETED'           -- BORROWED + DISBURSED
    WHEN l.STATUS = 0 AND l.STATUS_DETAIL = 3      THEN 'REPAYING'            -- BORROWING + DISBURSED
    WHEN l.STATUS = 2 AND l.STATUS_DETAIL = 0      THEN 'CANCELLED'           -- CANCELLED + ECONTRACT
    WHEN l.STATUS = 2                              THEN 'REJECTED'             -- CANCELLED + NOT_ELIGIBLE (12732)
    WHEN l.STATUS_DETAIL = 7                       THEN 'DISBURSED'            -- DISBURSEMENT_CONFIRMATION
    WHEN l.STATUS_DETAIL = 6                       THEN 'AWAITING_DISBURSEMENT' -- WAITING_FOR_DISBURSEMENT
    WHEN l.STATUS_DETAIL = 4                       THEN 'REPAYING'            -- OVERDUE (đang quá hạn)
    WHEN l.STATUS_DETAIL = 1                       THEN 'ACTIVE'              -- WAITING_FOR_INVESTMENT
    WHEN l.STATUS_DETAIL = 2                       THEN 'REJECTED'            -- NOT_ELIGIBLE
    ELSE                                            'PENDING_REVIEW'          -- ECONTRACT / chưa xử lý
  END,
  COALESCE(CAST(NULLIF(TRIM(l.MONEY_INVESTED), '') AS DECIMAL(15,2)), 0.00),
  CAST(NULLIF(TRIM(l.MONTHLY_INCOME), '') AS DECIMAL(15,2)),
  NULLIF(TRIM(l.WORK_UNIT), ''),
  NULLIF(TRIM(l.CURRENT_ADDRESS), ''),
  -- ref1: lấy phần TRƯỚC dấu " - " đầu tiên (hoặc toàn bộ nếu không có dấu " - ")
  NULLIF(TRIM(SUBSTRING_INDEX(NULLIF(TRIM(l.SURNAME_AND_NAME_OF_REFERENT), ''), ' - ', 1)), ''),
  NULLIF(TRIM(SUBSTRING_INDEX(NULLIF(TRIM(l.RELATIONSHIP_WITH_REFERENT), ''), ' - ', 1)), ''),
  LEFT(NULLIF(TRIM(SUBSTRING_INDEX(NULLIF(TRIM(l.PHONE_OF_REFERENT), ''), ' - ', 1)), ''), 20),
  -- ref2: lấy phần SAU dấu " - " đầu tiên (NULL nếu không có dấu " - ")
  CASE WHEN l.SURNAME_AND_NAME_OF_REFERENT   LIKE '% - %'
    THEN NULLIF(TRIM(SUBSTRING_INDEX(l.SURNAME_AND_NAME_OF_REFERENT, ' - ', -1)), '') END,
  CASE WHEN l.RELATIONSHIP_WITH_REFERENT LIKE '% - %'
    THEN NULLIF(TRIM(SUBSTRING_INDEX(l.RELATIONSHIP_WITH_REFERENT,  ' - ', -1)), '') END,
  CASE WHEN l.PHONE_OF_REFERENT LIKE '% - %'
    THEN LEFT(NULLIF(TRIM(SUBSTRING_INDEX(l.PHONE_OF_REFERENT, ' - ', -1)), ''), 20) END,
  -- loan_seq: extract số từ LOAN_CODE (VNF000003 → 3)
  CAST(SUBSTR(l.LOAN_CODE, 4) AS UNSIGNED),
  CASE WHEN l.IS_DELETED = 'Y' THEN 1 ELSE 0 END,
  l.CREATED_DATE,
  COALESCE(l.UPDATED_DATE, l.CREATED_DATE)
FROM tbl_user_loan l
JOIN auth_db.users u ON u.id = l.USER_ID   -- chỉ migrate loan của user đã migrate
LEFT JOIN tbl_loan_purpose p ON p.ID = l.ID_LOAN_PURPOSE
WHERE l.IS_DELETED = 'N'
  AND l.AMOUNT IS NOT NULL
  AND l.PERIOD IS NOT NULL
ON DUPLICATE KEY UPDATE
  product_id        = COALESCE(VALUES(product_id), product_id),
  status            = VALUES(status),
  funded_amount     = VALUES(funded_amount),
  ref1_full_name    = VALUES(ref1_full_name),
  ref1_relationship = VALUES(ref1_relationship),
  ref1_phone        = VALUES(ref1_phone),
  ref2_full_name    = VALUES(ref2_full_name),
  ref2_relationship = VALUES(ref2_relationship),
  ref2_phone        = VALUES(ref2_phone),
  updated_at        = VALUES(updated_at);
ENDSQL

LOANS=$($MYSQL APP_V2 --skip-column-names -e "SELECT COUNT(*) FROM loan_db.loan_requests;" 2>/dev/null)
log "  → loan_db.loan_requests tổng: ${LOANS}"

# ── Step 2: Migrate loan_db.repayment_schedule ───────────────────────────────
log "Step 2: Migrate tbl_payment_period → loan_db.repayment_schedule..."
$MYSQL APP_V2 <<'ENDSQL'
INSERT INTO loan_db.repayment_schedule
  (id, loan_id, period_number,
   due_date,
   principal_due, interest_due, total_due,
   paid_amount, paid_at,
   status, dpd,
   is_deleted, created_at, updated_at)
SELECT
  pp.ID,
  pp.USER_LOAN_ID,
  -- Lấy số kỳ từ TITLE "Kỳ thanh toán N"
  CAST(REGEXP_REPLACE(pp.TITLE, '[^0-9]', '') AS UNSIGNED),
  DATE(pp.PAYMENT_DATE),
  -- principal = tổng - lãi (không âm)
  GREATEST(
    COALESCE(CAST(NULLIF(TRIM(pp.PAYABLE), '') AS DECIMAL(15,2)), 0) -
    COALESCE(CAST(NULLIF(TRIM(pp.INTEREST_PAYABLE), '') AS DECIMAL(15,2)), 0),
    0
  ),
  COALESCE(CAST(NULLIF(TRIM(pp.INTEREST_PAYABLE), '') AS DECIMAL(15,2)), 0),
  COALESCE(CAST(NULLIF(TRIM(pp.PAYABLE), '') AS DECIMAL(15,2)), 0),
  COALESCE(CAST(NULLIF(TRIM(pp.MONEY_PAID), '') AS DECIMAL(15,2)), 0),
  CASE WHEN pp.STATUS = 1 THEN pp.UPDATE_DATE ELSE NULL END,
  CASE
    WHEN pp.STATUS = 1 THEN 'PAID'
    WHEN pp.STATUS = 2 THEN 'OVERDUE'
    ELSE                    'PENDING'
  END,
  COALESCE(pp.NUMBER_OF_DAYS_OVERDUE, 0),
  CASE WHEN pp.IS_DELETED = 'Y' THEN 1 ELSE 0 END,
  pp.CREATE_DATE,
  COALESCE(pp.UPDATE_DATE, pp.CREATE_DATE)
FROM tbl_payment_period pp
JOIN loan_db.loan_requests lr ON lr.id = pp.USER_LOAN_ID  -- chỉ lấy schedule của loan đã migrate
WHERE pp.IS_DELETED = 'N'
  AND pp.PAYABLE IS NOT NULL
  AND pp.PAYMENT_DATE IS NOT NULL
ON DUPLICATE KEY UPDATE
  status      = VALUES(status),
  paid_amount = VALUES(paid_amount),
  paid_at     = VALUES(paid_at),
  dpd         = VALUES(dpd),
  updated_at  = NOW();
ENDSQL

SCHEDULES=$($MYSQL APP_V2 --skip-column-names -e "SELECT COUNT(*) FROM loan_db.repayment_schedule;" 2>/dev/null)
log "  → loan_db.repayment_schedule tổng: ${SCHEDULES}"

# ── Step 3: Set activated_at cho khoản ACTIVE chưa có ────────────────────────
log "Step 3: Set activated_at = created_at cho ACTIVE loans chưa có..."
mysql -u"${DB_USER}" -p"${DB_PASS}" -h"${DB_HOST}" loan_db 2>/dev/null <<'ENDSQL'
UPDATE loan_requests
SET activated_at = created_at
WHERE status = 'ACTIVE'
  AND activated_at IS NULL
  AND is_deleted = 0;
ENDSQL
ACTIVATED=$(mysql -u"${DB_USER}" -p"${DB_PASS}" -h"${DB_HOST}" loan_db --skip-column-names -e \
  "SELECT COUNT(*) FROM loan_requests WHERE status='ACTIVE' AND activated_at IS NOT NULL;" 2>/dev/null)
log "  → ACTIVE loans có activated_at: ${ACTIVATED}"

log "===== Loan migration hoàn thành ====="
log "loan_requests:       ${LOANS}"
log "repayment_schedule:  ${SCHEDULES}"
