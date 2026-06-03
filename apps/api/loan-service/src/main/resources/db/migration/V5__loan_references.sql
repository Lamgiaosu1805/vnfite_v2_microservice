-- Thêm thông tin người tham chiếu (2 người: họ tên, mối quan hệ, SĐT, địa chỉ)
ALTER TABLE loan_requests
    ADD COLUMN ref1_full_name    VARCHAR(100) NULL AFTER referred_by,
    ADD COLUMN ref1_relationship VARCHAR(50)  NULL AFTER ref1_full_name,
    ADD COLUMN ref1_phone        VARCHAR(20)  NULL AFTER ref1_relationship,
    ADD COLUMN ref1_address      VARCHAR(500) NULL AFTER ref1_phone,
    ADD COLUMN ref2_full_name    VARCHAR(100) NULL AFTER ref1_address,
    ADD COLUMN ref2_relationship VARCHAR(50)  NULL AFTER ref2_full_name,
    ADD COLUMN ref2_phone        VARCHAR(20)  NULL AFTER ref2_relationship,
    ADD COLUMN ref2_address      VARCHAR(500) NULL AFTER ref2_phone;
