-- Dev/test seed data.
-- Only seed configuration data. Never truncate business/user data such as
-- loan_requests or loan_offers because UAT deploys also run with the test profile.

-- ============================================================
-- LOAN PRODUCTS (cấu hình sản phẩm — không phải user data)
-- ============================================================
INSERT INTO loan_products
  (id, code, name, category, product_group, profession_bound, description, min_amount, max_amount,
   available_terms, max_interest_rate, late_fee_rate, image_url, is_active, is_deleted, sort_order)
VALUES
  ('160a9ae5-ba81-4cbf-98ec-d803873647d6',
   'FAST', 'Gọi vốn siêu tốc', 'INDIVIDUAL', 1, 0,
   'Giải ngân nhanh trong ngày, không cần chứng minh tài sản. Phù hợp nhu cầu vốn ngắn hạn khẩn cấp.',
   1000000.00, 10000000.00, '1,3,6',
   NULL, 150.00,
   'https://service.vnfite.com.vn/static-file//images/loan/image/giaovien.png',
   1, 0, 1),

  ('003c9051-96b2-4a5f-8151-fd6a0d902500',
   'STUDENT', 'Gọi vốn dành cho sinh viên', 'INDIVIDUAL', 1, 1,
   'Sản phẩm thiết kế riêng cho sinh viên đang theo học tại các trường Đại học, Cao đẳng. Hỗ trợ chi phí học tập, sinh hoạt.',
   3000000.00, 20000000.00, '1,3,6,12',
   NULL, 150.00,
   'https://service.vnfite.com.vn/static-file//images/loan/image/giaovien.png',
   1, 0, 2),

  ('6bb7588a-197c-408d-927d-6b6744e68a4f',
   'CONSUMER', 'Gọi vốn mua sắm tiêu dùng', 'INDIVIDUAL', 2, 0,
   'Hỗ trợ tài chính cho các nhu cầu mua sắm hàng hóa, thiết bị gia đình, điện tử và các khoản tiêu dùng cá nhân.',
   1000000.00, 100000000.00, '1,3,6,12',
   NULL, 150.00,
   'https://service.vnfite.com.vn/static-file//images/loan/image/giaovien.png',
   1, 0, 3),

  ('d63f3610-98e9-4198-917d-8e03595062fa',
   'COSMETIC', 'Gọi vốn mục đích thẩm mỹ', 'INDIVIDUAL', 2, 0,
   'Tài trợ các dịch vụ làm đẹp, phẫu thuật thẩm mỹ tại các cơ sở được cấp phép.',
   3000000.00, 50000000.00, '1,3,6,9,12',
   90.00, 150.00,
   'https://service.vnfite.com.vn/static-file//images/loan/image/giaovien.png',
   1, 0, 4)
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  category = VALUES(category),
  product_group = VALUES(product_group),
  profession_bound = VALUES(profession_bound),
  description = VALUES(description),
  min_amount = VALUES(min_amount),
  max_amount = VALUES(max_amount),
  available_terms = VALUES(available_terms),
  max_interest_rate = VALUES(max_interest_rate),
  late_fee_rate = VALUES(late_fee_rate),
  image_url = VALUES(image_url),
  is_active = VALUES(is_active),
  is_deleted = VALUES(is_deleted),
  sort_order = VALUES(sort_order);
