-- CMS no longer mirrors customer/user or loan data.
-- Source of truth remains auth_db.users and loan_db.loan_requests.
DROP TABLE IF EXISTS daily_stats;
DROP TABLE IF EXISTS cms_loans;
DROP TABLE IF EXISTS cms_users;
