-- ═══════════════════════════════════════════════════════════════════════════
--  Nhóm B (CIC & lịch sử tín dụng) — bản nhập tay
--  Giai đoạn chờ API CIC sandbox NĐ94 go-live: thẩm định viên tra CIC ngoài rồi
--  nhập kết quả vào CMS; CMS gửi sang credit-service để chấm nhóm B thật.
--  Bổ sung 3 tiêu chí docx B1/B2/B4 (tối đa 140đ) vào nhóm B_CREDIT_HISTORY,
--  cộng cùng COMPLETED_LOANS (proxy nội bộ 50đ) → nhóm B khả dụng ~190/250.
--
--  Khi API go-live: chỉ thay nguồn nhập (API thay vì nhập tay), scorecard giữ nguyên.
--  An toàn dữ liệu: chỉ INSERT ... ON DUPLICATE KEY UPDATE bảng cấu hình. Idempotent.
-- ═══════════════════════════════════════════════════════════════════════════

INSERT INTO scoring_criteria
    (id, criteria_code, criteria_name, component, band_label, min_value, max_value, match_value, points, active, is_deleted)
VALUES
-- B1 — Nhóm nợ cao nhất hiện tại (CIC). Nhóm ≥3 = nợ xấu → cũng kích hoạt cổng loại trừ.
('2d0de615-314b-424b-b03e-7ddd15f4d213', 'CIC_DEBT_GROUP', 'B1. Nhóm nợ CIC', 'B_CREDIT_HISTORY', 'Nhóm 1 - đủ tiêu chuẩn', 1, 2,    NULL, 60, 1, 0),
('bec72093-43c9-4ccc-879e-4b635e353160', 'CIC_DEBT_GROUP', 'B1. Nhóm nợ CIC', 'B_CREDIT_HISTORY', 'Nhóm 2 - cần chú ý',    2, 3,    NULL, 40, 1, 0),
('5cfeeec6-23f5-4494-a6ba-2f792d395636', 'CIC_DEBT_GROUP', 'B1. Nhóm nợ CIC', 'B_CREDIT_HISTORY', 'Nhóm 3-5 - nợ xấu',     3, NULL, NULL, 0,  1, 0),

-- B2 — Số ngày quá hạn (DPD) cao nhất 12 tháng gần nhất.
('f92bf635-fbe0-4345-8dde-93e298b3947e', 'CIC_MAX_DPD', 'B2. DPD cao nhất 12 tháng', 'B_CREDIT_HISTORY', 'Không quá hạn',   0,  1,    NULL, 45, 1, 0),
('64a0792b-1a42-433a-8bc8-a272fd604520', 'CIC_MAX_DPD', 'B2. DPD cao nhất 12 tháng', 'B_CREDIT_HISTORY', 'DPD 1-9 ngày',    1,  10,   NULL, 30, 1, 0),
('29787705-a5e8-41d3-b829-e5d9ec7ccc90', 'CIC_MAX_DPD', 'B2. DPD cao nhất 12 tháng', 'B_CREDIT_HISTORY', 'DPD 10-29 ngày',  10, 30,   NULL, 15, 1, 0),
('b4da9bae-4b3f-431c-891f-3e42903bfac6', 'CIC_MAX_DPD', 'B2. DPD cao nhất 12 tháng', 'B_CREDIT_HISTORY', 'DPD ≥ 30 ngày',   30, NULL, NULL, 0,  1, 0),

-- B4 — Số tổ chức tín dụng đang có dư nợ (đa đầu mối làm tăng rủi ro quá tải).
('f9bae44a-d745-4969-b9b7-b1daf0970398', 'CIC_ACTIVE_LENDERS', 'B4. Số TCTD đang vay', 'B_CREDIT_HISTORY', '≤ 2 tổ chức',  0, 3,    NULL, 35, 1, 0),
('45133cb9-7b21-4c78-ad87-acf5e4e64226', 'CIC_ACTIVE_LENDERS', 'B4. Số TCTD đang vay', 'B_CREDIT_HISTORY', '3-4 tổ chức',  3, 5,    NULL, 20, 1, 0),
('6cc96bd7-73af-4eee-8de9-597434cbf47c', 'CIC_ACTIVE_LENDERS', 'B4. Số TCTD đang vay', 'B_CREDIT_HISTORY', '≥ 5 tổ chức',  5, NULL, NULL, 5,  1, 0)
ON DUPLICATE KEY UPDATE
    criteria_name = VALUES(criteria_name),
    component     = VALUES(component),
    min_value     = VALUES(min_value),
    max_value     = VALUES(max_value),
    match_value   = VALUES(match_value),
    points        = VALUES(points),
    active        = 1,
    is_deleted    = 0;
