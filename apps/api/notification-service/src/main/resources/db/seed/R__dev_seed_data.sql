-- Dev seed data — chỉ load khi SPRING_PROFILES_ACTIVE=dev
-- Xóa toàn bộ data cũ trước khi seed lại

SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE notifications;
TRUNCATE TABLE notification_templates;
SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================
-- NOTIFICATION TEMPLATES (cấu hình — không phải user data)
-- ============================================================
INSERT INTO notification_templates
  (id, code, title, body, type, is_deleted, created_at, updated_at)
VALUES
  ('62e82884-f767-4bc1-b16f-ba3116436a72',
   'LOAN_MATCHED',
   'Khoản vay của bạn có nhà đầu tư quan tâm',
   'Chúc mừng! Khoản vay #{loanId} của bạn vừa được ghép với nhà đầu tư phù hợp. Vui lòng kiểm tra và xác nhận.',
   'EMAIL', 0, NOW(), NOW()),

  ('6125864a-9c23-46e2-ae5d-16986a64b745',
   'KYC_APPROVED',
   'Xác thực danh tính thành công',
   'Thông tin eKYC của bạn đã được duyệt. Bạn có thể sử dụng đầy đủ tính năng của nền tảng.',
   'EMAIL', 0, NOW(), NOW()),

  ('11f2b4a1-72cd-4429-9605-2c08b3af693d',
   'KYC_REJECTED',
   'Xác thực danh tính chưa thành công',
   'Thông tin eKYC của bạn chưa được duyệt. Lý do: #{reason}. Vui lòng thử lại với thông tin chính xác hơn.',
   'EMAIL', 0, NOW(), NOW()),

  ('34e2ab8d-9a15-4d09-a1e0-b45e21921190',
   'REGISTRATION_SUCCESS',
   'Chào mừng bạn đến với VNFITE',
   'Tài khoản của bạn đã được tạo thành công với số điện thoại #{phone}. Hãy hoàn thành eKYC để mở khóa toàn bộ tính năng.',
   'EMAIL', 0, NOW(), NOW()),

  ('3d5288aa-f139-45ac-81d7-dc27e6b3aa06',
   'LOAN_FUNDED',
   'Khoản vay của bạn đã được giải ngân đủ vốn',
   'Khoản vay #{loanId} trị giá #{amount} VND đã được các nhà đầu tư tài trợ đầy đủ. Quá trình giải ngân sẽ được thực hiện sớm.',
   'EMAIL', 0, NOW(), NOW()),

  ('94c2dfd8-861d-41c3-9b2c-d59c80c5491f',
   'MATCH_OPPORTUNITY',
   'Cơ hội đầu tư mới phù hợp với bạn',
   'Có khoản vay mới phù hợp với tiêu chí đầu tư của bạn: #{loanAmount} VND, lãi suất #{interestRate}%/năm, #{termMonths} tháng.',
   'EMAIL', 0, NOW(), NOW());
