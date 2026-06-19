#!/bin/bash
# =============================================================================
# Migrate loan products: tbl_loan_product_package (APP_V2) → loan_db.loan_products
# Đồng thời cập nhật loan_requests.product_id từ tbl_user_loan.LOAN_PRODUCT_PACKAGE_ID
#
# Chạy trực tiếp trên server 118:
#   bash /root/p2p-lending/infra/scripts/migrate-products.sh
#
# Idempotent — chạy nhiều lần không bị lỗi.
#
# Mapping:
#   tbl_loan_product_package.TYPE: 0=INDIVIDUAL, 1=BUSINESS
#   tbl_loan_product_package.IS_RELEASE: 1=active, 0=draft
#   tbl_term.PERIOD → available_terms (GROUP_CONCAT)
#   image_url: prefix với https://service.vnfite.com.vn/static-file
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

# ── Step 1: Migrate loan_db.loan_products ─────────────────────────────────────
log "Step 1: Migrate tbl_loan_product_package → loan_db.loan_products..."
$MYSQL APP_V2 <<'ENDSQL'
INSERT INTO loan_db.loan_products
  (id, code, name, category, product_group, profession_bound, description,
   min_amount, max_amount, available_terms,
   max_interest_rate, late_fee_rate, repayment_method, image_url,
   is_active, is_deleted, sort_order, created_at, updated_at)
SELECT
  p.ID,
  -- Code: prefix + 8 ký tự đầu UUID để đảm bảo unique, không đụng seed cũ
  CONCAT('V1_', UPPER(SUBSTRING(p.ID, 1, 8))),
  TRIM(p.LOAN_PACKAGE_NAME),
  -- TYPE: 1=BUSINESS, 0/NULL=INDIVIDUAL
  CASE WHEN p.TYPE = 1 THEN 'BUSINESS' ELSE 'INDIVIDUAL' END,
  -- product_group: 2=business, 1=individual
  CASE WHEN p.TYPE = 1 THEN 2 ELSE 1 END,
  -- profession_bound: 1 nếu sản phẩm dành riêng cho nghề
  CASE
    WHEN LOWER(CONVERT(p.LOAN_PACKAGE_NAME USING utf8mb4)) LIKE '%giáo viên%'
      OR LOWER(CONVERT(p.LOAN_PACKAGE_NAME USING utf8mb4)) LIKE '%b%c s%'
      OR LOWER(CONVERT(p.LOAN_PACKAGE_NAME USING utf8mb4)) LIKE '%công chức%'
      OR LOWER(CONVERT(p.LOAN_PACKAGE_NAME USING utf8mb4)) LIKE '%công nhân%'
      OR LOWER(CONVERT(p.LOAN_PACKAGE_NAME USING utf8mb4)) LIKE '%tài xế%'
      OR UPPER(CONVERT(p.LOAN_PACKAGE_NAME USING utf8mb4)) LIKE '%CBNV%'
    THEN 1 ELSE 0
  END,
  -- description từ CONTENT (longblob → utf8mb4 text)
  NULLIF(TRIM(CONVERT(p.CONTENT USING utf8mb4)), ''),
  -- amount: varchar trong hệ thống cũ, cần bỏ dấu phẩy nếu có
  CAST(REPLACE(COALESCE(p.MINIMUM_AMOUNT, '0'), ',', '') AS DECIMAL(15,2)),
  CAST(REPLACE(COALESCE(p.MAXIMUM_AMOUNT, '0'), ',', '') AS DECIMAL(15,2)),
  -- available_terms: lấy từ tbl_term, group thành chuỗi "3,6,9,12"
  COALESCE(
    (SELECT GROUP_CONCAT(t.PERIOD ORDER BY t.PERIOD SEPARATOR ',')
     FROM tbl_term t
     WHERE t.LOAN_PRODUCT_PACKAGE_ID = p.ID AND t.IS_DELETED = 'N'),
    '3,6,9,12'
  ),
  NULL,          -- max_interest_rate: chưa có trong hệ thống cũ
  150.00,        -- late_fee_rate mặc định
  'EMI_MONTHLY', -- repayment_method mặc định
  -- image_url: thêm prefix service URL
  CASE
    WHEN p.IMAGE IS NULL OR TRIM(p.IMAGE) = '' THEN NULL
    ELSE CONCAT('https://service.vnfite.com.vn/static-file', TRIM(p.IMAGE))
  END,
  CASE WHEN p.IS_RELEASE = 1 THEN 1 ELSE 0 END,
  CASE WHEN p.IS_DELETED = 'Y' THEN 1 ELSE 0 END,
  -- sort_order: không có trong hệ thống cũ, dùng 99 (CMS sẽ sắp xếp thủ công sau)
  99,
  p.CREATE_DATE,
  COALESCE(p.UPDATE_DATE, p.CREATE_DATE)
FROM tbl_loan_product_package p
ON DUPLICATE KEY UPDATE
  name            = VALUES(name),
  description     = VALUES(description),
  min_amount      = VALUES(min_amount),
  max_amount      = VALUES(max_amount),
  available_terms = VALUES(available_terms),
  image_url       = VALUES(image_url),
  is_active       = VALUES(is_active),
  is_deleted      = VALUES(is_deleted),
  updated_at      = NOW();
ENDSQL

PRODUCTS=$($MYSQL loan_db --skip-column-names -e "SELECT COUNT(*) FROM loan_products;" 2>/dev/null)
log "  → loan_db.loan_products tổng: ${PRODUCTS}"

# ── Step 2: Cập nhật loan_requests.product_id ─────────────────────────────────
log "Step 2: Cập nhật loan_requests.product_id từ tbl_user_loan.LOAN_PRODUCT_PACKAGE_ID..."
$MYSQL APP_V2 <<'ENDSQL'
UPDATE loan_db.loan_requests lr
JOIN APP_V2.tbl_user_loan ul ON ul.ID = lr.id
JOIN loan_db.loan_products p ON p.id = ul.LOAN_PRODUCT_PACKAGE_ID
SET lr.product_id = ul.LOAN_PRODUCT_PACKAGE_ID
WHERE lr.product_id IS NULL
  AND ul.LOAN_PRODUCT_PACKAGE_ID IS NOT NULL
  AND ul.LOAN_PRODUCT_PACKAGE_ID != '';
ENDSQL

LINKED=$($MYSQL loan_db --skip-column-names -e \
  "SELECT COUNT(*) FROM loan_requests WHERE product_id IS NOT NULL;" 2>/dev/null)
TOTAL=$($MYSQL loan_db --skip-column-names -e \
  "SELECT COUNT(*) FROM loan_requests;" 2>/dev/null)
log "  → loan_requests có product_id: ${LINKED}/${TOTAL}"

# ── Tóm tắt ──────────────────────────────────────────────────────────────────
log "===== Product migration hoàn thành ====="
log "loan_products:          ${PRODUCTS}"
log "loan_requests linked:   ${LINKED}/${TOTAL}"
