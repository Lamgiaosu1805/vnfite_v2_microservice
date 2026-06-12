-- ═══════════════════════════════════════════════════════════════════════════
--  Tái cấu trúc scorecard theo khung VNFITE Credit Score 360 (docx V1.0)
--  Nhóm A–H theo tài liệu. GIAI ĐOẠN HIỆN TẠI chỉ bật các tiêu chí lấy được
--  từ dữ liệu hệ thống đang có + file chứng từ khách upload (AI phân tích).
--  Các nhóm chưa có nguồn dữ liệu (CIC đầy đủ, dòng tiền ngân hàng, device,
--  AML/graph) để ở "roadmap" — KHÔNG seed, không kéo tụt điểm hồ sơ.
--
--  Nguyên tắc theo docx:
--   - Không chấm trực tiếp biến nhân khẩu học nhạy cảm (tuổi/hôn nhân/người
--     phụ thuộc/học vấn) → chống phân biệt đối xử (mục 4 & 6.7).
--   - Affordability dùng tỷ lệ PTI/DTI + mức xác minh thu nhập, không chấm
--     mức thu nhập tuyệt đối (tránh thiên lệch người thu nhập cao).
--   - Chứng từ AI nuôi 3 tín hiệu: xác minh thu nhập (C1), chứng từ nghề (E3),
--     toàn vẹn chứng từ/chống gian lận (H2).
--
--  An toàn dữ liệu: chỉ UPDATE cờ active + INSERT ... ON DUPLICATE KEY UPDATE
--  trên bảng cấu hình scoring_criteria. Không TRUNCATE/DELETE. Idempotent.
-- ═══════════════════════════════════════════════════════════════════════════

-- ── 1. Vô hiệu hóa các tiêu chí KHÔNG nằm trong khung Credit Score 360 ────────
--     (giữ nguyên bản ghi để truy vết lịch sử, chỉ tắt active)
UPDATE scoring_criteria SET active = 0
 WHERE criteria_code IN ('AGE','MARITAL_STATUS','DEPENDENTS','EDUCATION_LEVEL','HAS_REFERRER','MONTHLY_INCOME');

-- ── 2. Vô hiệu hóa band cũ của các tiêu chí được tái cấu trúc lại điểm/nhóm ───
--     (band mới chèn ở bước 3; band trùng band_label sẽ được ON DUPLICATE bật lại)
UPDATE scoring_criteria SET active = 0
 WHERE criteria_code IN ('KYC_STATUS','COMPLETED_LOANS','DTI_RATIO','EMPLOYMENT_YEARS',
                         'OCCUPATION_TYPE','LOAN_TO_ANNUAL_INCOME','ACCOUNT_AGE_MONTHS');

-- ── 3. Khung A–H giai đoạn "dữ liệu hiện có" (tổng tối đa ~320 điểm thô) ──────
INSERT INTO scoring_criteria
    (id, criteria_code, criteria_name, component, band_label, min_value, max_value, match_value, points, active, is_deleted)
VALUES
-- ╔══ A · KYC & ĐỊNH DANH (docx nhóm A, tối đa khả dụng 20) ══════════════════╗
('f47d5adf-e810-44d0-98e5-79ef450e70ba', 'KYC_STATUS', 'A1. Định danh eKYC', 'A_KYC', 'Đã định danh (APPROVED)', NULL, NULL, 'APPROVED', 20, 1, 0),
('cdcd5e32-7978-4c6e-923c-9448a41eda27', 'KYC_STATUS', 'A1. Định danh eKYC', 'A_KYC', 'Đang chờ duyệt (PENDING)', NULL, NULL, 'PENDING', 8, 1, 0),

-- ╔══ B · LỊCH SỬ TÍN DỤNG NỘI BỘ (proxy cho CIC khi chưa nối CIC; tối đa 50) ═╗
('931bfe8d-cc7b-4315-9ad2-bf00b4164819', 'COMPLETED_LOANS', 'B. Lịch sử trả nợ nội bộ VNFITE', 'B_CREDIT_HISTORY', 'Chưa có khoản hoàn thành', 0, 1,    NULL, 10, 1, 0),
('bb46252a-a06d-4d5f-9ea2-b5d0552efc65', 'COMPLETED_LOANS', 'B. Lịch sử trả nợ nội bộ VNFITE', 'B_CREDIT_HISTORY', '1 khoản đã hoàn thành',    1, 2,    NULL, 30, 1, 0),
('247c0df7-22f8-4ed9-92f3-e83eca8fe2d7', 'COMPLETED_LOANS', 'B. Lịch sử trả nợ nội bộ VNFITE', 'B_CREDIT_HISTORY', '2 khoản trở lên',          2, NULL, NULL, 50, 1, 0),

-- ╔══ C · KHẢ NĂNG TRẢ NỢ / AFFORDABILITY (docx nhóm C, tối đa khả dụng 125) ══╗
-- C1. Mức xác minh thu nhập — kết hợp thu nhập khai báo + kết quả AI đọc chứng từ
('23d6ea9f-9c43-4bb0-a666-b6e6182b7a2d', 'INCOME_VERIFICATION', 'C1. Mức xác minh thu nhập', 'C_AFFORDABILITY', 'Có chứng từ AI xác minh khớp', NULL, NULL, 'VERIFIED',      45, 1, 0),
('7c5808f3-90f6-4fa4-a375-03d5affa9f9b', 'INCOME_VERIFICATION', 'C1. Mức xác minh thu nhập', 'C_AFFORDABILITY', 'Có chứng từ bổ trợ',          NULL, NULL, 'SUPPORTED',     30, 1, 0),
('6074f195-084d-4f60-a42e-4adefc9b2e7e', 'INCOME_VERIFICATION', 'C1. Mức xác minh thu nhập', 'C_AFFORDABILITY', 'Chỉ tự khai, chưa chứng từ',  NULL, NULL, 'DECLARED_ONLY', 15, 1, 0),
-- C2. PTI — nghĩa vụ trả nợ khoản mới / thu nhập tháng
('abac6b4f-4d24-48d7-9340-80578e5515c9', 'PTI_RATIO', 'C2. PTI - Trả nợ kỳ/thu nhập (%)', 'C_AFFORDABILITY', 'PTI ≤ 25%',  0,  25,   NULL, 45, 1, 0),
('5d27304b-b9a6-4d81-b1c5-c8f3fd79f18c', 'PTI_RATIO', 'C2. PTI - Trả nợ kỳ/thu nhập (%)', 'C_AFFORDABILITY', 'PTI 25-35%', 25, 35,   NULL, 35, 1, 0),
('a048542c-b256-47bb-992a-8960fba5e2a6', 'PTI_RATIO', 'C2. PTI - Trả nợ kỳ/thu nhập (%)', 'C_AFFORDABILITY', 'PTI 35-45%', 35, 45,   NULL, 20, 1, 0),
('0aade2ed-ff04-4b18-bbea-1300a445e546', 'PTI_RATIO', 'C2. PTI - Trả nợ kỳ/thu nhập (%)', 'C_AFFORDABILITY', 'PTI > 45%',  45, NULL, NULL, 5,  1, 0),
-- C3. DTI — tổng nghĩa vụ nợ / thu nhập
('71bd8b7d-534c-4acf-a057-99c8fc10042c', 'DTI_RATIO', 'C3. DTI - Tổng nợ/thu nhập (%)', 'C_AFFORDABILITY', 'DTI ≤ 40%',  0,  40,   NULL, 35, 1, 0),
('23fce265-c5a8-41f8-9bf5-ca5d7659c8e3', 'DTI_RATIO', 'C3. DTI - Tổng nợ/thu nhập (%)', 'C_AFFORDABILITY', 'DTI 40-50%', 40, 50,   NULL, 25, 1, 0),
('c862c88b-14c5-43b0-9a09-2bea1f1b5f59', 'DTI_RATIO', 'C3. DTI - Tổng nợ/thu nhập (%)', 'C_AFFORDABILITY', 'DTI 50-60%', 50, 60,   NULL, 10, 1, 0),
('ef7d5a2c-f13b-4203-9149-755825924d43', 'DTI_RATIO', 'C3. DTI - Tổng nợ/thu nhập (%)', 'C_AFFORDABILITY', 'DTI > 60%',  60, NULL, NULL, 0,  1, 0),

-- ╔══ E · NGHỀ NGHIỆP & ỔN ĐỊNH THU NHẬP (docx nhóm E, tối đa khả dụng 65) ════╗
-- E1. Thâm niên công tác/hoạt động (năm)
('61abc159-f44e-464d-b444-e16b51674c2f', 'EMPLOYMENT_YEARS', 'E1. Thâm niên công tác/kinh doanh', 'E_OCCUPATION', 'Dưới 6 tháng', 0,   0.5, NULL, 5,  1, 0),
('cd0de89d-e785-46d3-b030-22ea3daa8f08', 'EMPLOYMENT_YEARS', 'E1. Thâm niên công tác/kinh doanh', 'E_OCCUPATION', '6-12 tháng',   0.5, 1,   NULL, 10, 1, 0),
('399eaee2-5412-41fb-a540-e4977c7d8371', 'EMPLOYMENT_YEARS', 'E1. Thâm niên công tác/kinh doanh', 'E_OCCUPATION', '1-2 năm',      1,   2,   NULL, 18, 1, 0),
('b3006d09-61e1-408a-9e5f-ce863a18f3d7', 'EMPLOYMENT_YEARS', 'E1. Thâm niên công tác/kinh doanh', 'E_OCCUPATION', 'Trên 2 năm',   2,   NULL,NULL, 25, 1, 0),
-- E2. Tính ổn định nghề/ngành (từ loại nghề nghiệp)
('0afdcb62-f8e0-4cf1-9d30-d6ec6c84acf0', 'OCCUPATION_TYPE', 'E2. Ổn định nghề/ngành', 'E_OCCUPATION', 'Công chức/viên chức',    NULL, NULL, 'GOV_EMPLOYEE',   20, 1, 0),
('4de066eb-4e30-4122-9a50-4cdc5507fa9f', 'OCCUPATION_TYPE', 'E2. Ổn định nghề/ngành', 'E_OCCUPATION', 'Hưởng lương HĐLĐ',       NULL, NULL, 'SALARIED',       16, 1, 0),
('d663fa0a-d3e4-4916-80f7-55f6e41871b5', 'OCCUPATION_TYPE', 'E2. Ổn định nghề/ngành', 'E_OCCUPATION', 'Chủ kinh doanh có ĐKKD', NULL, NULL, 'BUSINESS_OWNER', 14, 1, 0),
('224f6020-f947-475f-868e-7fbcb7e2ead3', 'OCCUPATION_TYPE', 'E2. Ổn định nghề/ngành', 'E_OCCUPATION', 'Lao động tự do',         NULL, NULL, 'FREELANCER',     8,  1, 0),
('12b77b17-4d2a-4bb5-9efa-0f9ca9bde28a', 'OCCUPATION_TYPE', 'E2. Ổn định nghề/ngành', 'E_OCCUPATION', 'Khác',                   NULL, NULL, 'OTHER',          5,  1, 0),
-- E3. Chứng từ nghề nghiệp/kinh doanh (AI xác nhận HĐLĐ/ĐKKD)
('ee7817c9-5746-494c-828a-7e469fa87c78', 'OCCUPATION_DOC', 'E3. Chứng từ nghề nghiệp/kinh doanh', 'E_OCCUPATION', 'AI xác nhận chứng từ nghề',  NULL, NULL, 'CONFIRMED', 20, 1, 0),
('ecaf1696-b0fd-448e-92d3-ea96ec14a670', 'OCCUPATION_DOC', 'E3. Chứng từ nghề nghiệp/kinh doanh', 'E_OCCUPATION', 'Đã nộp, cần kiểm tra thêm',  NULL, NULL, 'PROVIDED',  10, 1, 0),
('4ce93e33-1495-42e3-a084-d16b6abb09fe', 'OCCUPATION_DOC', 'E3. Chứng từ nghề nghiệp/kinh doanh', 'E_OCCUPATION', 'Chưa có chứng từ nghề',      NULL, NULL, 'NONE',      0,  1, 0),

-- ╔══ F · ĐẶC ĐIỂM KHOẢN VAY & QUAN HỆ KH (docx nhóm F, tối đa khả dụng 45) ═══╗
-- F1. Số tiền vay / thu nhập năm
('f9e52df8-05e5-47d0-967a-2efd60718b0e', 'LOAN_TO_ANNUAL_INCOME', 'F1. Tiền vay/thu nhập năm (%)', 'F_LOAN', 'Dưới 20%', 0,  20,   NULL, 20, 1, 0),
('049d70de-127d-4c3a-afc2-f3070886a151', 'LOAN_TO_ANNUAL_INCOME', 'F1. Tiền vay/thu nhập năm (%)', 'F_LOAN', '20-40%',   20, 40,   NULL, 13, 1, 0),
('3f64e39c-372d-41d2-9cef-0b91dc0097a5', 'LOAN_TO_ANNUAL_INCOME', 'F1. Tiền vay/thu nhập năm (%)', 'F_LOAN', '40-60%',   40, 60,   NULL, 6,  1, 0),
('6e89bc6b-9482-4546-980d-b54f17cc2cf1', 'LOAN_TO_ANNUAL_INCOME', 'F1. Tiền vay/thu nhập năm (%)', 'F_LOAN', 'Trên 60%', 60, NULL, NULL, 0,  1, 0),
-- F3. Mục đích vay rõ ràng
('d1e1b7ba-d2fd-472a-b83d-7b448b5f08e8', 'PURPOSE_CLARITY', 'F3. Mục đích vay rõ ràng', 'F_LOAN', 'Rõ ràng, cụ thể',  NULL, NULL, 'CLEAR', 10, 1, 0),
('ac547572-af3c-4fae-a251-07bfcfbe226c', 'PURPOSE_CLARITY', 'F3. Mục đích vay rõ ràng', 'F_LOAN', 'Sơ sài, cần bổ sung', NULL, NULL, 'VAGUE', 5,  1, 0),
('cc35e053-345b-455b-9368-f585a3c1463d', 'PURPOSE_CLARITY', 'F3. Mục đích vay rõ ràng', 'F_LOAN', 'Không nêu mục đích',  NULL, NULL, 'NONE',  0,  1, 0),
-- F4. Quan hệ với VNFITE (tuổi tài khoản)
('5713fd3c-ca09-4c1b-952e-6245ccc98759', 'ACCOUNT_AGE_MONTHS', 'F4. Quan hệ với VNFITE', 'F_LOAN', 'Dưới 3 tháng',  0,  3,    NULL, 4,  1, 0),
('62ff3927-1b27-4d5b-bad4-8df9f790838c', 'ACCOUNT_AGE_MONTHS', 'F4. Quan hệ với VNFITE', 'F_LOAN', '3-6 tháng',     3,  6,    NULL, 8,  1, 0),
('221e1d26-e23e-4ea5-a95a-5e0cb499a31b', 'ACCOUNT_AGE_MONTHS', 'F4. Quan hệ với VNFITE', 'F_LOAN', '6-12 tháng',    6,  12,   NULL, 12, 1, 0),
('74a1c7d8-c58f-45ae-be84-5a9f610cd073', 'ACCOUNT_AGE_MONTHS', 'F4. Quan hệ với VNFITE', 'F_LOAN', 'Trên 12 tháng', 12, NULL, NULL, 15, 1, 0),

-- ╔══ H · GIAN LẬN & BẤT THƯỜNG CHỨNG TỪ (docx nhóm H2, tối đa khả dụng 15) ═══╗
-- H2. Toàn vẹn chứng từ — từ verdict AI đọc tất cả file đính kèm
('c213071b-d04c-45bc-b628-ae5047192871', 'DOCUMENT_INTEGRITY', 'H2. Toàn vẹn chứng từ', 'H_FRAUD', 'Chứng từ nhất quán',     NULL, NULL, 'CLEAN',   15, 1, 0),
('daba8ed9-f0f9-474f-bd48-98039e094fe3', 'DOCUMENT_INTEGRITY', 'H2. Toàn vẹn chứng từ', 'H_FRAUD', 'Có điểm cần kiểm tra',   NULL, NULL, 'REVIEW',  8,  1, 0),
('2410e38b-f0dc-469f-ae91-aeca53e1cd4d', 'DOCUMENT_INTEGRITY', 'H2. Toàn vẹn chứng từ', 'H_FRAUD', 'Có dấu hiệu rủi ro cao', NULL, NULL, 'FLAGGED', 0,  1, 0)
ON DUPLICATE KEY UPDATE
    criteria_name = VALUES(criteria_name),
    component     = VALUES(component),
    min_value     = VALUES(min_value),
    max_value     = VALUES(max_value),
    match_value   = VALUES(match_value),
    points        = VALUES(points),
    active        = 1,
    is_deleted    = 0;
