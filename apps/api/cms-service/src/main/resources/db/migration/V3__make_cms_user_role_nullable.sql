-- role is no longer sent in user.registered event — make nullable so existing rows are unaffected
ALTER TABLE cms_users DROP INDEX idx_cu_role;
ALTER TABLE cms_users MODIFY COLUMN role VARCHAR(20) NULL;
