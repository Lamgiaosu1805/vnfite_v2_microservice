-- Xóa các tài khoản admin mặc định (admin/ops) được tạo từ seed cũ và CmsAdminSeeder.
-- Từ đây trở đi, admin được tạo thủ công qua /cms/auth/setup (lần đầu)
-- hoặc qua tính năng Quản lý Admin trên CMS web.
TRUNCATE TABLE cms_admin_users;
