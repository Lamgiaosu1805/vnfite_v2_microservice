-- One-time migration for existing environments.
-- Run this on the MySQL server before switching cms-service from CUSTOMER_DB to CMS_DB.
--
-- It keeps CMS internal admin data and removes old mirror/read-model tables.

CREATE DATABASE IF NOT EXISTS cms_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cms_db.flyway_schema_history LIKE customer_db.flyway_schema_history;
INSERT IGNORE INTO cms_db.flyway_schema_history
SELECT * FROM customer_db.flyway_schema_history;

CREATE TABLE IF NOT EXISTS cms_db.cms_admin_users LIKE customer_db.cms_admin_users;
INSERT IGNORE INTO cms_db.cms_admin_users
SELECT * FROM customer_db.cms_admin_users;

DROP TABLE IF EXISTS cms_db.daily_stats;
DROP TABLE IF EXISTS cms_db.cms_loans;
DROP TABLE IF EXISTS cms_db.cms_users;

GRANT ALL PRIVILEGES ON cms_db.* TO 'p2p_user'@'%';
