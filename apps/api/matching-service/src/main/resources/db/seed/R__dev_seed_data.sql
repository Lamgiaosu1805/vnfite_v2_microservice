-- Dev seed data — chỉ load khi SPRING_PROFILES_ACTIVE=dev
-- Dùng INSERT IGNORE để idempotent

-- ============================================================
-- INVESTOR PREFERENCES
-- investor1: thích vay 10-50tr, lãi 10-15%, 6-24 tháng
-- investor2: thích vay 5-30tr, lãi 8-18%, 3-12 tháng
-- ============================================================
INSERT IGNORE INTO investor_preferences (id, investor_id, min_investment_amount, max_investment_amount, min_interest_rate, max_interest_rate, min_term_months, max_term_months, active, is_deleted, created_at, updated_at) VALUES
  ('b1000001-0000-0000-0000-000000000001', 'd3000003-0000-0000-0000-000000000003', 10000000.00, 50000000.00, 10.00, 15.00,  6, 24, 1, 0, NOW(), NOW()),
  ('b2000002-0000-0000-0000-000000000002', 'd4000004-0000-0000-0000-000000000004',  5000000.00, 30000000.00,  8.00, 18.00,  3, 12, 1, 0, NOW(), NOW());

-- ============================================================
-- PENDING LOANS — snapshot khoản vay chờ matching
-- loan2 đang PENDING (chưa fully_funded)
-- loan3 đã FUNDED (fully_funded = 1, scheduler bỏ qua)
-- ============================================================
INSERT IGNORE INTO pending_loans (loan_id, borrower_id, amount, interest_rate, term_months, purpose, fully_funded, is_deleted, received_at, last_matched_at, updated_at) VALUES
  ('e2000002-0000-0000-0000-000000000002', 'd2000002-0000-0000-0000-000000000002', 100000000.00, 15.00, 24, 'Đầu tư thiết bị sản xuất — xưởng may gia đình', 0, 0, DATE_SUB(NOW(), INTERVAL 2 DAY), NULL,                            NOW()),
  ('e3000003-0000-0000-0000-000000000003', 'd1000001-0000-0000-0000-000000000001',  30000000.00, 10.00,  6, 'Vốn lưu động ngắn hạn — bổ sung hàng tồn kho',  1, 0, DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_SUB(NOW(), INTERVAL 9 DAY), NOW());

-- ============================================================
-- MATCH RECORDS — loan1 được matched với 2 investor
-- ============================================================
INSERT IGNORE INTO match_records (id, loan_id, investor_id, score, status, is_deleted, created_at, updated_at) VALUES
  ('a1000001-0000-0000-0000-000000000001', 'e1000001-0000-0000-0000-000000000001', 'd3000003-0000-0000-0000-000000000003', 0.850, 'ACCEPTED', 0, DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY)),
  ('a2000002-0000-0000-0000-000000000002', 'e1000001-0000-0000-0000-000000000001', 'd4000004-0000-0000-0000-000000000004', 0.720, 'ACCEPTED', 0, DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY));
