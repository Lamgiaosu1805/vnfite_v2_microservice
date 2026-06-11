-- ═══════════════════════════════════════════════════════════════════════════
--  Scorecard mặc định v1.0 — tổng điểm thô tối đa 730, chuẩn hóa về 300-850
--  Idempotent: ON DUPLICATE KEY UPDATE theo unique (criteria_code, band_label)
--  Đổi trọng số: UPDATE bảng này, không cần deploy lại code
-- ═══════════════════════════════════════════════════════════════════════════

INSERT INTO scoring_criteria
    (id, criteria_code, criteria_name, component, band_label, min_value, max_value, match_value, points, active, is_deleted)
VALUES
-- ── Nhân khẩu học (max 180) ──────────────────────────────────────────────────
('7c4d14ea-62b2-4432-b9c6-a7c85fbd152d', 'AGE', 'Độ tuổi', 'DEMOGRAPHIC', '18-22 tuổi',        18, 23,  NULL, 20,  1, 0),
('c3b6a557-561d-4d46-a831-55f0503c1d1b', 'AGE', 'Độ tuổi', 'DEMOGRAPHIC', '23-30 tuổi',        23, 31,  NULL, 45,  1, 0),
('b83366f3-d929-43cb-9c9d-fd3c83641015', 'AGE', 'Độ tuổi', 'DEMOGRAPHIC', '31-45 tuổi',        31, 46,  NULL, 60,  1, 0),
('0a92c549-6b69-4c15-ab55-b638161f78be', 'AGE', 'Độ tuổi', 'DEMOGRAPHIC', '46-55 tuổi',        46, 56,  NULL, 45,  1, 0),
('57d4baf9-a112-4818-a249-626ddf34317f', 'AGE', 'Độ tuổi', 'DEMOGRAPHIC', 'Trên 55 tuổi',      56, NULL, NULL, 25, 1, 0),

('e0bd7261-b292-49e9-87f5-6aaf7093901b', 'MARITAL_STATUS', 'Tình trạng hôn nhân', 'DEMOGRAPHIC', 'Đã kết hôn', NULL, NULL, 'MARRIED',  40, 1, 0),
('48d86b15-6ecd-40f5-942b-bc94017abb51', 'MARITAL_STATUS', 'Tình trạng hôn nhân', 'DEMOGRAPHIC', 'Độc thân',   NULL, NULL, 'SINGLE',   25, 1, 0),
('97572470-8e53-4e5b-89de-cc56d608ca76', 'MARITAL_STATUS', 'Tình trạng hôn nhân', 'DEMOGRAPHIC', 'Góa',        NULL, NULL, 'WIDOWED',  20, 1, 0),
('a60b15b2-77cf-4382-890e-881c100de33f', 'MARITAL_STATUS', 'Tình trạng hôn nhân', 'DEMOGRAPHIC', 'Ly hôn',     NULL, NULL, 'DIVORCED', 15, 1, 0),

('5f0c9901-5d12-41ce-bb69-2fb90c137046', 'DEPENDENTS', 'Số người phụ thuộc', 'DEMOGRAPHIC', 'Không có',           0, 1,    NULL, 40, 1, 0),
('c15c64a7-8ead-4765-9f22-4181311cae3d', 'DEPENDENTS', 'Số người phụ thuộc', 'DEMOGRAPHIC', '1-2 người',          1, 3,    NULL, 30, 1, 0),
('93dba901-9421-4d6c-bd94-1cf760b6c827', 'DEPENDENTS', 'Số người phụ thuộc', 'DEMOGRAPHIC', '3 người trở lên',    3, NULL, NULL, 15, 1, 0),

('97a3f066-0e31-464d-bbb2-59a0742da1d3', 'EDUCATION_LEVEL', 'Trình độ học vấn', 'DEMOGRAPHIC', 'Sau đại học',        NULL, NULL, 'POSTGRAD',    40, 1, 0),
('f212cf96-8a29-4ea9-836c-e44943a0500c', 'EDUCATION_LEVEL', 'Trình độ học vấn', 'DEMOGRAPHIC', 'Đại học',            NULL, NULL, 'UNIVERSITY',  35, 1, 0),
('feef5828-10bb-42be-86dc-69307ea75650', 'EDUCATION_LEVEL', 'Trình độ học vấn', 'DEMOGRAPHIC', 'Cao đẳng/Trung cấp', NULL, NULL, 'COLLEGE',     30, 1, 0),
('5bb1b143-9dca-45da-8122-b8bbb75e4a2f', 'EDUCATION_LEVEL', 'Trình độ học vấn', 'DEMOGRAPHIC', 'THPT',               NULL, NULL, 'HIGH_SCHOOL', 20, 1, 0),
('92151694-1d06-4b1f-98b6-48d8c8c01482', 'EDUCATION_LEVEL', 'Trình độ học vấn', 'DEMOGRAPHIC', 'Khác',               NULL, NULL, 'OTHER',       10, 1, 0),

-- ── Nghề nghiệp & thu nhập (max 250) ─────────────────────────────────────────
('8c28aa05-9b8c-47a6-8759-ee11783aa5b1', 'OCCUPATION_TYPE', 'Loại nghề nghiệp', 'INCOME', 'Công chức/viên chức',     NULL, NULL, 'GOV_EMPLOYEE',   80, 1, 0),
('fb6dac90-dc35-45d8-a06a-35e1f712e8b8', 'OCCUPATION_TYPE', 'Loại nghề nghiệp', 'INCOME', 'Nhân viên HĐ lao động',   NULL, NULL, 'SALARIED',       70, 1, 0),
('87f0886e-a388-45e3-a4e9-19b2cedba1d2', 'OCCUPATION_TYPE', 'Loại nghề nghiệp', 'INCOME', 'Chủ kinh doanh có ĐKKD',  NULL, NULL, 'BUSINESS_OWNER', 60, 1, 0),
('fd9a5a01-b4ee-42dc-b19e-34adeee54376', 'OCCUPATION_TYPE', 'Loại nghề nghiệp', 'INCOME', 'Lao động tự do',          NULL, NULL, 'FREELANCER',     30, 1, 0),
('ab832ab0-106d-4dec-8186-7a4eba23dd98', 'OCCUPATION_TYPE', 'Loại nghề nghiệp', 'INCOME', 'Khác',                    NULL, NULL, 'OTHER',          20, 1, 0),

('a42d436b-dde7-417b-93b9-f59feb1ffdf5', 'EMPLOYMENT_YEARS', 'Thâm niên làm việc', 'INCOME', 'Dưới 1 năm', 0, 1,    NULL, 20, 1, 0),
('359d3499-8d1b-4038-aa3d-b06f8748d680', 'EMPLOYMENT_YEARS', 'Thâm niên làm việc', 'INCOME', '1-3 năm',    1, 3,    NULL, 40, 1, 0),
('e14ae657-f163-4b22-90d2-d2716584d7c3', 'EMPLOYMENT_YEARS', 'Thâm niên làm việc', 'INCOME', 'Trên 3 năm', 3, NULL, NULL, 60, 1, 0),

('d04d180a-ce27-40cc-a841-58d44369342d', 'MONTHLY_INCOME', 'Thu nhập hàng tháng', 'INCOME', 'Dưới 5 triệu',  0,        5000000,  NULL, 15,  1, 0),
('237f5a7b-1ed0-4069-9b7c-8880e12747b4', 'MONTHLY_INCOME', 'Thu nhập hàng tháng', 'INCOME', '5-10 triệu',    5000000,  10000000, NULL, 35,  1, 0),
('e3036d59-2cfb-4d5b-ac40-a2693910201d', 'MONTHLY_INCOME', 'Thu nhập hàng tháng', 'INCOME', '10-20 triệu',   10000000, 20000000, NULL, 60,  1, 0),
('31b3f960-fdb8-4f84-b5c8-db3b13f0ae14', 'MONTHLY_INCOME', 'Thu nhập hàng tháng', 'INCOME', '20-30 triệu',   20000000, 30000000, NULL, 90,  1, 0),
('eb8e2fb2-b0f3-4d20-a798-0e2ac9280b0a', 'MONTHLY_INCOME', 'Thu nhập hàng tháng', 'INCOME', 'Trên 30 triệu', 30000000, NULL,     NULL, 110, 1, 0),

-- ── Quan hệ tín dụng (max 140) ───────────────────────────────────────────────
('0bf7dc22-eb80-4164-826c-26604538f840', 'DTI_RATIO', 'Tỷ lệ nợ/thu nhập (%)', 'CREDIT_HISTORY', 'Dưới 30%', 0,  30,   NULL, 90, 1, 0),
('9ce68b56-9833-4da9-83ed-c00fd39169cb', 'DTI_RATIO', 'Tỷ lệ nợ/thu nhập (%)', 'CREDIT_HISTORY', '30-50%',   30, 50,   NULL, 60, 1, 0),
('b53972c8-26e6-46fc-ad1b-234d46699752', 'DTI_RATIO', 'Tỷ lệ nợ/thu nhập (%)', 'CREDIT_HISTORY', '50-70%',   50, 70,   NULL, 30, 1, 0),
('e784b4da-514e-43f5-9a8b-8e7dfd7ed3ed', 'DTI_RATIO', 'Tỷ lệ nợ/thu nhập (%)', 'CREDIT_HISTORY', 'Trên 70%', 70, NULL, NULL, 0,  1, 0),

('5a07f726-3ce0-499e-a39d-ad3974ee266a', 'COMPLETED_LOANS', 'Khoản vay đã hoàn thành trên VNFITE', 'CREDIT_HISTORY', 'Chưa có',           0, 1,    NULL, 10, 1, 0),
('2836c103-0466-4498-b4c5-99a8f6899195', 'COMPLETED_LOANS', 'Khoản vay đã hoàn thành trên VNFITE', 'CREDIT_HISTORY', '1 khoản',           1, 2,    NULL, 25, 1, 0),
('ac4a81a8-be3f-49d4-b216-1436faee1bc5', 'COMPLETED_LOANS', 'Khoản vay đã hoàn thành trên VNFITE', 'CREDIT_HISTORY', '2 khoản trở lên',   2, NULL, NULL, 50, 1, 0),

-- ── Hành vi trên nền tảng (max 110) ──────────────────────────────────────────
('edf725e2-5149-4028-a6f2-452c734a70ae', 'ACCOUNT_AGE_MONTHS', 'Tuổi tài khoản (tháng)', 'PLATFORM', 'Dưới 3 tháng',   0,  3,    NULL, 10, 1, 0),
('59c87804-3881-4a85-8997-f62a1cd004bd', 'ACCOUNT_AGE_MONTHS', 'Tuổi tài khoản (tháng)', 'PLATFORM', '3-6 tháng',      3,  6,    NULL, 20, 1, 0),
('fb7575ab-e2df-4634-a99f-2a258a64837c', 'ACCOUNT_AGE_MONTHS', 'Tuổi tài khoản (tháng)', 'PLATFORM', '6-12 tháng',     6,  12,   NULL, 30, 1, 0),
('5d91bf9c-98d1-4a6e-8e0e-748217858ff2', 'ACCOUNT_AGE_MONTHS', 'Tuổi tài khoản (tháng)', 'PLATFORM', 'Trên 12 tháng',  12, NULL, NULL, 40, 1, 0),

('589b4ae6-b621-4877-b070-3ff76876a58f', 'KYC_STATUS', 'Trạng thái eKYC', 'PLATFORM', 'Đã định danh', NULL, NULL, 'APPROVED', 40, 1, 0),

('5b90352d-6d9d-463b-a472-3ba812a59ac0', 'HAS_REFERRER', 'Người giới thiệu', 'PLATFORM', 'Có người giới thiệu', NULL, NULL, 'YES', 30, 1, 0),
('e8b693bb-9482-4d41-a84e-ea5b1315dae7', 'HAS_REFERRER', 'Người giới thiệu', 'PLATFORM', 'Không có',            NULL, NULL, 'NO',  15, 1, 0),

-- ── Đặc điểm khoản gọi vốn (max 50) ──────────────────────────────────────────
('91e8471d-5680-4e97-bd6d-1ac78e58a228', 'LOAN_TO_ANNUAL_INCOME', 'Tiền vay/thu nhập năm (%)', 'LOAN', 'Dưới 20%', 0,  20,   NULL, 50, 1, 0),
('25e69c5b-eeca-4aad-b523-3459ba5eef55', 'LOAN_TO_ANNUAL_INCOME', 'Tiền vay/thu nhập năm (%)', 'LOAN', '20-40%',   20, 40,   NULL, 35, 1, 0),
('44a5f06b-1aa2-4195-aef9-a93309bea793', 'LOAN_TO_ANNUAL_INCOME', 'Tiền vay/thu nhập năm (%)', 'LOAN', '40-60%',   40, 60,   NULL, 20, 1, 0),
('5754e06f-502c-4f6f-a719-43046180b7b8', 'LOAN_TO_ANNUAL_INCOME', 'Tiền vay/thu nhập năm (%)', 'LOAN', 'Trên 60%', 60, NULL, NULL, 5,  1, 0)
ON DUPLICATE KEY UPDATE
    criteria_name = VALUES(criteria_name),
    component     = VALUES(component),
    min_value     = VALUES(min_value),
    max_value     = VALUES(max_value),
    match_value   = VALUES(match_value),
    points        = VALUES(points),
    active        = VALUES(active);
