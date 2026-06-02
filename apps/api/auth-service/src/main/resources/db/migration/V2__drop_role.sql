-- Remove role column — all customers are USER, admin access is via CMS (cms_admin_users)
ALTER TABLE users DROP COLUMN role;
