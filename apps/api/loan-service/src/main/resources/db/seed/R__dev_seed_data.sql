-- Dev seed data — chỉ load khi SPRING_PROFILES_ACTIVE=dev hoặc test
-- Dùng INSERT IGNORE để idempotent

-- ============================================================
-- LOAN REQUESTS
-- loan1: đã được CMS duyệt → ACTIVE, đang nhận offer (35/50 triệu)
-- loan2: vừa nộp hồ sơ → PENDING_REVIEW, chờ CMS thẩm định
-- loan3: đã FUNDED đủ vốn
-- ============================================================
INSERT IGNORE INTO loan_requests
  (id, borrower_id, amount, interest_rate, term_months, purpose,
   occupation, monthly_income, current_address,
   status, funded_amount, is_deleted, created_at, updated_at, reviewed_at)
VALUES
  ('e1000001-0000-0000-0000-000000000001',
   'd1000001-0000-0000-0000-000000000001',
   50000000.00, 12.50, 12, 'Kinh doanh nhỏ lẻ — mở quán cà phê',
   'Tiểu thương', 15000000.00, '123 Nguyễn Trãi, P. Bến Thành, Q.1, TP.HCM',
   'ACTIVE', 35000000.00, 0,
   DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY),
   DATE_SUB(NOW(), INTERVAL 4 DAY)),

  ('e2000002-0000-0000-0000-000000000002',
   'd2000002-0000-0000-0000-000000000002',
   100000000.00, NULL, 24, 'Đầu tư thiết bị sản xuất — xưởng may gia đình',
   'Chủ xưởng may', 25000000.00, '456 Lê Văn Việt, P. Hiệp Phú, TP. Thủ Đức, TP.HCM',
   'PENDING_REVIEW', 0.00, 0,
   DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY),
   NULL),

  ('e3000003-0000-0000-0000-000000000003',
   'd1000001-0000-0000-0000-000000000001',
   30000000.00, 10.00, 6, 'Vốn lưu động ngắn hạn — bổ sung hàng tồn kho',
   'Tiểu thương', 15000000.00, '123 Nguyễn Trãi, P. Bến Thành, Q.1, TP.HCM',
   'FUNDED', 30000000.00, 0,
   DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY),
   DATE_SUB(NOW(), INTERVAL 9 DAY));

-- ============================================================
-- LOAN OFFERS
-- investor1 → loan1 (20tr, ACCEPTED)
-- investor2 → loan1 (15tr, ACCEPTED)
-- investor1 → loan3 (30tr, ACCEPTED — fully funded)
-- ============================================================
INSERT IGNORE INTO loan_offers
  (id, loan_request_id, investor_id, amount, status, is_deleted, created_at, updated_at)
VALUES
  ('f1000001-0000-0000-0000-000000000001',
   'e1000001-0000-0000-0000-000000000001',
   'd3000003-0000-0000-0000-000000000003',
   20000000.00, 'ACCEPTED', 0,
   DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY)),

  ('f2000002-0000-0000-0000-000000000002',
   'e1000001-0000-0000-0000-000000000001',
   'd4000004-0000-0000-0000-000000000004',
   15000000.00, 'ACCEPTED', 0,
   DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY)),

  ('f3000003-0000-0000-0000-000000000003',
   'e3000003-0000-0000-0000-000000000003',
   'd3000003-0000-0000-0000-000000000003',
   30000000.00, 'ACCEPTED', 0,
   DATE_SUB(NOW(), INTERVAL 9 DAY), DATE_SUB(NOW(), INTERVAL 9 DAY));
