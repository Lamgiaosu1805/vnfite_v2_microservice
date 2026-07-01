#!/bin/bash
# =============================================================================
# Sync blacklist từ hệ thống cũ APP_V2.tbl_user sang auth_db.users.
# Idempotent, không xóa user, không reset dữ liệu nghiệp vụ.
#
# Chạy trên live/test server backend:
#   bash /root/p2p-lending/infra/scripts/sync-blacklisted-users.sh
# =============================================================================

set -euo pipefail

ENV_FILE="/root/p2p-lending/.env"
if [[ -f "$ENV_FILE" ]]; then
  set -a; source "$ENV_FILE"; set +a
fi

DB_HOST="${MYSQL_HOST:-127.0.0.1}"
DB_USER="${DB_USERNAME:-vnfite}"
DB_PASS="${DB_PASSWORD:-Vnfite080620!@#}"

MYSQL="mysql -u${DB_USER} -p${DB_PASS} -h${DB_HOST} --default-character-set=utf8mb4"

log() { echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*"; }

log "Sync blacklist APP_V2.tbl_user → auth_db.users..."

$MYSQL auth_db <<'SQL'
DROP PROCEDURE IF EXISTS ensure_blacklist_columns;

DELIMITER //
CREATE PROCEDURE ensure_blacklist_columns()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'blacklisted'
    ) THEN
        ALTER TABLE users ADD COLUMN blacklisted TINYINT(1) NOT NULL DEFAULT 0;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'blacklisted_at'
    ) THEN
        ALTER TABLE users ADD COLUMN blacklisted_at DATETIME NULL;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'blacklist_source'
    ) THEN
        ALTER TABLE users ADD COLUMN blacklist_source VARCHAR(50) NULL;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'blacklist_reason'
    ) THEN
        ALTER TABLE users ADD COLUMN blacklist_reason VARCHAR(255) NULL;
    END IF;
END//
DELIMITER ;

CALL ensure_blacklist_columns();

DROP PROCEDURE ensure_blacklist_columns;
SQL

$MYSQL <<'SQL'
UPDATE auth_db.users u
JOIN APP_V2.tbl_user old_u
  ON old_u.USER_NAME = u.phone
 AND old_u.IS_DELETED = 'N'
SET u.blacklisted = CASE WHEN old_u.IS_BLOCKED = 'Y' THEN 1 ELSE 0 END,
    u.blacklisted_at = CASE WHEN old_u.IS_BLOCKED = 'Y' THEN old_u.BLOCKED_DATE ELSE NULL END,
    u.blacklist_source = CASE WHEN old_u.IS_BLOCKED = 'Y' THEN 'APP_V2.tbl_user' ELSE NULL END,
    u.blacklist_reason = CASE WHEN old_u.IS_BLOCKED = 'Y' THEN 'Đồng bộ blacklist từ hệ thống cũ' ELSE NULL END,
    u.updated_at = NOW()
WHERE COALESCE(old_u.IS_BLOCKED, 'N') IN ('Y', 'N');
SQL

OLD_COUNT=$($MYSQL APP_V2 --skip-column-names -e "SELECT COUNT(*) FROM tbl_user WHERE IS_DELETED='N' AND IS_BLOCKED='Y';" 2>/dev/null)
NEW_COUNT=$($MYSQL auth_db --skip-column-names -e "SELECT COUNT(*) FROM users WHERE is_deleted=0 AND blacklisted=1;" 2>/dev/null)

log "Blacklist hệ thống cũ: ${OLD_COUNT:-0}"
log "Blacklist hệ thống mới: ${NEW_COUNT:-0}"
log "Sync blacklist hoàn thành."
