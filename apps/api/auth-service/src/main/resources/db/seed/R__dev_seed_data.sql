-- Dev seed data — chỉ load khi SPRING_PROFILES_ACTIVE=dev
-- Mật khẩu tất cả users: Test@1234
-- Dùng INSERT IGNORE để idempotent (chạy lại không bị lỗi duplicate)

-- ============================================================
-- USERS
-- Seed 4 users: 2 borrower, 2 investor, 1 admin
-- ============================================================
INSERT IGNORE INTO users (id, phone, password, email, kyc_status, referred_by, is_deleted, created_at, updated_at) VALUES
  ('d1000001-0000-0000-0000-000000000001', '0901111111', '$2y$10$9IFqvA.Y8ITJUfvPqai3S.yeYblrWiAVE2mOjhJmLMARDF1WWGXJu', 'vayone@dev.local',  'APPROVED', NULL,          0, NOW(), NOW()),
  ('d2000002-0000-0000-0000-000000000002', '0902222222', '$2y$10$9IFqvA.Y8ITJUfvPqai3S.yeYblrWiAVE2mOjhJmLMARDF1WWGXJu', 'vaytwo@dev.local',  'NONE',     NULL,          0, NOW(), NOW()),
  ('d3000003-0000-0000-0000-000000000003', '0903333333', '$2y$10$9IFqvA.Y8ITJUfvPqai3S.yeYblrWiAVE2mOjhJmLMARDF1WWGXJu', 'invone@dev.local',  'APPROVED', NULL,          0, NOW(), NOW()),
  ('d4000004-0000-0000-0000-000000000004', '0904444444', '$2y$10$9IFqvA.Y8ITJUfvPqai3S.yeYblrWiAVE2mOjhJmLMARDF1WWGXJu', 'invtwo@dev.local',  'APPROVED', '0903333333',  0, NOW(), NOW()),
  ('d5000005-0000-0000-0000-000000000005', '0905555555', '$2y$10$9IFqvA.Y8ITJUfvPqai3S.yeYblrWiAVE2mOjhJmLMARDF1WWGXJu', 'admin@dev.local',   'APPROVED', NULL,          0, NOW(), NOW());

-- ============================================================
-- KYC SUBMISSIONS — cho borrower1, investor1, investor2, admin
-- ============================================================
INSERT IGNORE INTO kyc_submissions (id, user_id, cccd_number, full_name, gender, date_of_birth, permanent_address, hometown, issue_date, issuing_authority, expiry_date, front_image_id, back_image_id, portrait_image_id, status, is_deleted, created_at, updated_at) VALUES
  ('k1000001-0000-0000-0000-000000000001', 'd1000001-0000-0000-0000-000000000001', '001111111111', 'Nguyễn Văn Vay 1',      'MALE',   '1990-01-15', '123 Lê Lợi, Q1, TP.HCM',    'Nam Định',     '2020-01-15', 'Cục CS QLHC về TTXH',  '2035-01-15', 'mock_k1front', 'mock_k1back', 'mock_k1portrait', 'APPROVED', 0, NOW(), NOW()),
  ('k2000002-0000-0000-0000-000000000002', 'd3000003-0000-0000-0000-000000000003', '003333333333', 'Lê Văn Đầu Tư 1',       'MALE',   '1985-06-20', '456 Nguyễn Trãi, Q5, TP.HCM','Hà Nội',       '2018-06-20', 'Cục CS QLHC về TTXH',  '2033-06-20', 'mock_k2front', 'mock_k2back', 'mock_k2portrait', 'APPROVED', 0, NOW(), NOW()),
  ('k3000003-0000-0000-0000-000000000003', 'd4000004-0000-0000-0000-000000000004', '004444444444', 'Phạm Thị Đầu Tư 2',     'FEMALE', '1992-09-10', '789 Trần Hưng Đạo, Q1, TP.HCM','Đà Nẵng',  '2022-09-10', 'Cục CS QLHC về TTXH',  NULL,         'mock_k3front', 'mock_k3back', 'mock_k3portrait', 'APPROVED', 0, NOW(), NOW()),
  ('k4000004-0000-0000-0000-000000000004', 'd5000005-0000-0000-0000-000000000005', '005555555555', 'Admin Test',            'MALE',   '1988-03-25', '100 Đinh Tiên Hoàng, Q1, TP.HCM','Hải Phòng', '2019-03-25', 'Cục CS QLHC về TTXH',  '2034-03-25', 'mock_k4front', 'mock_k4back', 'mock_k4portrait', 'APPROVED', 0, NOW(), NOW());
