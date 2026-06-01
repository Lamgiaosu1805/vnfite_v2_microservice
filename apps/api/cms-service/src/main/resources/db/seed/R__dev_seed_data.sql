-- Dev seed data — chỉ load khi SPRING_PROFILES_ACTIVE=dev
-- Dùng INSERT IGNORE để idempotent

-- ============================================================
-- CMS ADMIN USERS — đăng nhập CMS portal
-- username: admin / password: Admin@1234
-- username: ops   / password: Admin@1234
-- ============================================================
INSERT IGNORE INTO cms_admin_users (id, username, email, full_name, password, role, active, is_deleted, created_at, updated_at) VALUES
  ('c1000001-0000-0000-0000-000000000001', 'admin', 'admin@dev.local', 'Super Admin',     '$2y$10$R3xlpvd8fU6nWKPv7tjqEuAxsRt0Bxm5zMxNSGmy2tyrtRHidCYAW', 'ADMIN', 1, 0, NOW(), NOW()),
  ('c2000002-0000-0000-0000-000000000002', 'ops',   'ops@dev.local',   'Operations Team', '$2y$10$R3xlpvd8fU6nWKPv7tjqEuAxsRt0Bxm5zMxNSGmy2tyrtRHidCYAW', 'OPS',   1, 0, NOW(), NOW());

-- ============================================================
-- CMS USERS — mirror của auth_db.users qua Kafka
-- ============================================================
INSERT IGNORE INTO cms_users (user_id, phone, email, full_name, role, kyc_status, account_status, is_deleted, created_at, updated_at) VALUES
  ('d1000001-0000-0000-0000-000000000001', '0901111111', 'vayone@dev.local',  'Nguyễn Văn Vay 1',  'USER',  'APPROVED', 'ACTIVE',    0, NOW(), NOW()),
  ('d2000002-0000-0000-0000-000000000002', '0902222222', 'vaytwo@dev.local',  'Trần Thị Vay 2',    'USER',  'NONE',     'ACTIVE',    0, NOW(), NOW()),
  ('d3000003-0000-0000-0000-000000000003', '0903333333', 'invone@dev.local',  'Lê Văn Đầu Tư 1',   'USER',  'APPROVED', 'ACTIVE',    0, NOW(), NOW()),
  ('d4000004-0000-0000-0000-000000000004', '0904444444', 'invtwo@dev.local',  'Phạm Thị Đầu Tư 2', 'USER',  'APPROVED', 'ACTIVE',    0, NOW(), NOW()),
  ('d5000005-0000-0000-0000-000000000005', '0905555555', 'admin@dev.local',   'Admin Test',         'ADMIN', 'APPROVED', 'ACTIVE',    0, NOW(), NOW());

-- ============================================================
-- CMS LOANS — mirror của loan_db.loan_requests qua Kafka
-- ============================================================
INSERT IGNORE INTO cms_loans (loan_id, borrower_id, amount, interest_rate, term_months, purpose, status, is_deleted, created_at, updated_at) VALUES
  ('e1000001-0000-0000-0000-000000000001', 'd1000001-0000-0000-0000-000000000001',  50000000.00, 12.50, 12, 'Kinh doanh nhỏ lẻ — mở quán cà phê',             'ACTIVE',  0, DATE_SUB(NOW(), INTERVAL 5 DAY),  NOW()),
  ('e2000002-0000-0000-0000-000000000002', 'd2000002-0000-0000-0000-000000000002', 100000000.00, 15.00, 24, 'Đầu tư thiết bị sản xuất — xưởng may gia đình',   'PENDING', 0, DATE_SUB(NOW(), INTERVAL 2 DAY),  NOW()),
  ('e3000003-0000-0000-0000-000000000003', 'd1000001-0000-0000-0000-000000000001',  30000000.00, 10.00,  6, 'Vốn lưu động ngắn hạn — bổ sung hàng tồn kho',   'FUNDED',  0, DATE_SUB(NOW(), INTERVAL 10 DAY), NOW());

-- ============================================================
-- DAILY STATS — 3 ngày gần nhất
-- ============================================================
INSERT IGNORE INTO daily_stats (id, stat_date, new_users, new_loans, funded_loans, loan_volume, is_deleted, created_at, updated_at) VALUES
  ('s1000001-0000-0000-0000-000000000001', DATE_SUB(CURDATE(), INTERVAL 2 DAY), 3, 1, 0,  50000000.00, 0, NOW(), NOW()),
  ('s2000002-0000-0000-0000-000000000002', DATE_SUB(CURDATE(), INTERVAL 1 DAY), 2, 1, 1,  30000000.00, 0, NOW(), NOW()),
  ('s3000003-0000-0000-0000-000000000003', CURDATE(),                           0, 1, 0, 100000000.00, 0, NOW(), NOW());
