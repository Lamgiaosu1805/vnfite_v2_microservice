-- Dev seed data — chỉ load khi SPRING_PROFILES_ACTIVE=dev hoặc test
-- Dùng INSERT IGNORE để idempotent

-- ============================================================
-- LOAN PRODUCTS (4 sản phẩm gọi vốn cá nhân)
-- ============================================================
INSERT IGNORE INTO loan_products
  (id, code, name, category, description, min_amount, max_amount,
   available_terms, max_interest_rate, late_fee_rate, image_url, is_active, is_deleted, sort_order)
VALUES
  ('prod-fast-0000-0000-000000000001',
   'FAST', 'Gọi vốn siêu tốc', 'INDIVIDUAL',
   'Giải ngân nhanh trong ngày, không cần chứng minh tài sản. Phù hợp nhu cầu vốn ngắn hạn khẩn cấp.',
   1000000.00, 10000000.00, '1,3,6',
   NULL, 150.00,
   'https://service.vnfite.com.vn/static-file//images/loan/image/giaovien.png',
   1, 0, 1),

  ('prod-stud-0000-0000-000000000002',
   'STUDENT', 'Gọi vốn dành cho sinh viên', 'INDIVIDUAL',
   'Sản phẩm thiết kế riêng cho sinh viên đang theo học tại các trường Đại học, Cao đẳng. Hỗ trợ chi phí học tập, sinh hoạt.',
   3000000.00, 20000000.00, '1,3,6,12',
   NULL, 150.00,
   'https://service.vnfite.com.vn/static-file//images/loan/image/giaovien.png',
   1, 0, 2),

  ('prod-cons-0000-0000-000000000003',
   'CONSUMER', 'Gọi vốn mua sắm tiêu dùng', 'INDIVIDUAL',
   'Hỗ trợ tài chính cho các nhu cầu mua sắm hàng hóa, thiết bị gia đình, điện tử và các khoản tiêu dùng cá nhân.',
   1000000.00, 100000000.00, '1,3,6,12',
   NULL, 150.00,
   'https://service.vnfite.com.vn/static-file//images/loan/image/giaovien.png',
   1, 0, 3),

  ('prod-cosm-0000-0000-000000000004',
   'COSMETIC', 'Gọi vốn mục đích thẩm mỹ', 'INDIVIDUAL',
   'Tài trợ các dịch vụ làm đẹp, phẫu thuật thẩm mỹ tại các cơ sở được cấp phép. Lãi suất tối đa 90%/năm.',
   3000000.00, 50000000.00, '1,3,6,9,12',
   90.00, 150.00,
   'https://service.vnfite.com.vn/static-file//images/loan/image/giaovien.png',
   1, 0, 4);

-- ============================================================
-- LOAN REQUESTS
-- loan1: đã được CMS duyệt → ACTIVE, đang nhận offer (35/50 triệu)
-- loan2: vừa nộp hồ sơ → PENDING_REVIEW, chờ CMS thẩm định
-- loan3: đã FUNDED đủ vốn
-- ============================================================
INSERT IGNORE INTO loan_requests
  (id, product_id, borrower_id, amount, interest_rate, term_months, purpose,
   occupation, monthly_income, current_address,
   status, funded_amount, is_deleted, created_at, updated_at, reviewed_at)
VALUES
  ('e1000001-0000-0000-0000-000000000001',
   'prod-cons-0000-0000-000000000003',
   'd1000001-0000-0000-0000-000000000001',
   50000000.00, 12.50, 12, 'Kinh doanh nhỏ lẻ — mở quán cà phê',
   'Tiểu thương', 15000000.00, '123 Nguyễn Trãi, P. Bến Thành, Q.1, TP.HCM',
   'ACTIVE', 35000000.00, 0,
   DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY),
   DATE_SUB(NOW(), INTERVAL 4 DAY)),

  ('e2000002-0000-0000-0000-000000000002',
   'prod-cons-0000-0000-000000000003',
   'd2000002-0000-0000-0000-000000000002',
   100000000.00, NULL, 24, 'Đầu tư thiết bị sản xuất — xưởng may gia đình',
   'Chủ xưởng may', 25000000.00, '456 Lê Văn Việt, P. Hiệp Phú, TP. Thủ Đức, TP.HCM',
   'PENDING_REVIEW', 0.00, 0,
   DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY),
   NULL),

  ('e3000003-0000-0000-0000-000000000003',
   'prod-fast-0000-0000-000000000001',
   'd1000001-0000-0000-0000-000000000001',
   30000000.00, 10.00, 6, 'Vốn lưu động ngắn hạn — bổ sung hàng tồn kho',
   'Tiểu thương', 15000000.00, '123 Nguyễn Trãi, P. Bến Thành, Q.1, TP.HCM',
   'FUNDED', 30000000.00, 0,
   DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY),
   DATE_SUB(NOW(), INTERVAL 9 DAY));

-- ============================================================
-- LOAN OFFERS
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
