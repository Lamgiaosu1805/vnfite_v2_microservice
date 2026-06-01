-- Dev seed data — chỉ load khi SPRING_PROFILES_ACTIVE=dev
-- Dùng INSERT IGNORE để idempotent

-- ============================================================
-- NOTIFICATION TEMPLATES
-- ============================================================
INSERT IGNORE INTO notification_templates (id, code, title, body, type, is_deleted, created_at, updated_at) VALUES
  ('t1000001-0000-0000-0000-000000000001', 'LOAN_MATCHED',         'Khoản vay của bạn có nhà đầu tư quan tâm',   'Chúc mừng! Khoản vay #{loanId} của bạn vừa được ghép với nhà đầu tư phù hợp. Vui lòng kiểm tra và xác nhận.',             'EMAIL', 0, NOW(), NOW()),
  ('t2000002-0000-0000-0000-000000000002', 'KYC_APPROVED',         'Xác thực danh tính thành công',              'Thông tin eKYC của bạn đã được duyệt. Bạn có thể sử dụng đầy đủ tính năng của nền tảng.',                             'EMAIL', 0, NOW(), NOW()),
  ('t3000003-0000-0000-0000-000000000003', 'KYC_REJECTED',         'Xác thực danh tính chưa thành công',         'Thông tin eKYC của bạn chưa được duyệt. Lý do: #{reason}. Vui lòng thử lại với thông tin chính xác hơn.',            'EMAIL', 0, NOW(), NOW()),
  ('t4000004-0000-0000-0000-000000000004', 'REGISTRATION_SUCCESS', 'Chào mừng bạn đến với P2P Lending',          'Tài khoản của bạn đã được tạo thành công với số điện thoại #{phone}. Hãy hoàn thành eKYC để mở khóa toàn bộ tính năng.', 'EMAIL', 0, NOW(), NOW()),
  ('t5000005-0000-0000-0000-000000000005', 'LOAN_FUNDED',          'Khoản vay của bạn đã được giải ngân đủ vốn', 'Khoản vay #{loanId} trị giá #{amount} VND đã được các nhà đầu tư tài trợ đầy đủ. Quá trình giải ngân sẽ được thực hiện sớm.', 'EMAIL', 0, NOW(), NOW()),
  ('t6000006-0000-0000-0000-000000000006', 'MATCH_OPPORTUNITY',    'Cơ hội đầu tư mới phù hợp với bạn',         'Có khoản vay mới phù hợp với tiêu chí đầu tư của bạn: #{loanAmount} VND, lãi suất #{interestRate}%/năm, #{termMonths} tháng.', 'EMAIL', 0, NOW(), NOW());

-- ============================================================
-- NOTIFICATIONS — mẫu thông báo đã gửi
-- ============================================================
INSERT IGNORE INTO notifications (id, user_id, title, message, type, channel, is_read, sent_at, is_deleted, created_at, updated_at) VALUES
  ('n1000001-0000-0000-0000-000000000001', 'd1000001-0000-0000-0000-000000000001', 'Xác thực danh tính thành công',              'Thông tin eKYC của bạn đã được duyệt.',                                    'EMAIL', 'email', 1, DATE_SUB(NOW(), INTERVAL 4 DAY), 0, DATE_SUB(NOW(), INTERVAL 4 DAY), NOW()),
  ('n2000002-0000-0000-0000-000000000002', 'd1000001-0000-0000-0000-000000000001', 'Khoản vay của bạn có nhà đầu tư quan tâm',  'Khoản vay e1000001 của bạn vừa được ghép với nhà đầu tư.',                 'EMAIL', 'email', 0, DATE_SUB(NOW(), INTERVAL 1 DAY), 0, DATE_SUB(NOW(), INTERVAL 1 DAY), NOW()),
  ('n3000003-0000-0000-0000-000000000003', 'd3000003-0000-0000-0000-000000000003', 'Cơ hội đầu tư mới phù hợp với bạn',         'Có khoản vay mới: 50,000,000 VND, lãi 12.5%/năm, 12 tháng.',              'EMAIL', 'email', 1, DATE_SUB(NOW(), INTERVAL 4 DAY), 0, DATE_SUB(NOW(), INTERVAL 4 DAY), NOW()),
  ('n4000004-0000-0000-0000-000000000004', 'd4000004-0000-0000-0000-000000000004', 'Cơ hội đầu tư mới phù hợp với bạn',         'Có khoản vay mới phù hợp: 50,000,000 VND, lãi 12.5%/năm, 12 tháng.',     'EMAIL', 'email', 1, DATE_SUB(NOW(), INTERVAL 3 DAY), 0, DATE_SUB(NOW(), INTERVAL 3 DAY), NOW());
