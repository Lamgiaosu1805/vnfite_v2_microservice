#!/bin/bash
set -e
source /root/p2p-lending/.env

echo "===== Migrate news từ APP_V2 → loan_db ====="

mysql -u"${DB_USERNAME}" -p"${DB_PASSWORD}" -h 127.0.0.1 loan_db 2>/dev/null <<'SQL'
INSERT INTO news (id, title, subtitle, image_url, content, news_type, published_at, is_deleted)
SELECT
    n.ID,
    n.MAIN_TITLE,
    n.SUB_TITLE,
    CASE
        WHEN n.IMAGE IS NOT NULL AND n.IMAGE != ''
        THEN CASE
            WHEN TRIM(n.IMAGE) LIKE '/images/%' THEN TRIM(n.IMAGE)
            ELSE CONCAT('/images/', TRIM(LEADING '/' FROM TRIM(n.IMAGE)))
        END
        ELSE NULL
    END,
    CAST(n.CONTENT AS CHAR CHARACTER SET utf8mb4),
    n.TYPE,
    n.CREATED_DATE,
    CASE WHEN n.IS_DELETE = 'Y' OR n.IS_DELETED = 'Y' THEN 1 ELSE 0 END
FROM APP_V2.tbl_news n
WHERE n.ID IS NOT NULL
ON DUPLICATE KEY UPDATE
    title       = VALUES(title),
    subtitle    = VALUES(subtitle),
    image_url   = VALUES(image_url),
    content     = VALUES(content),
    news_type   = VALUES(news_type),
    published_at = VALUES(published_at),
    is_deleted  = VALUES(is_deleted);
SQL

COUNT=$(mysql -u"${DB_USERNAME}" -p"${DB_PASSWORD}" -h 127.0.0.1 loan_db -sNe "SELECT COUNT(*) FROM news;" 2>/dev/null)
echo "  → loan_db.news tổng: ${COUNT}"
echo "===== Migrate news hoàn thành ====="
