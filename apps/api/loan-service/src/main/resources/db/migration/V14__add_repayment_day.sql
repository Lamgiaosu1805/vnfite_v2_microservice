-- Ngày trả nợ hàng tháng: chỉ cho phép ngày 5 hoặc ngày 20 theo quy định nghiệp vụ VNFITE.
-- NULL = chưa chọn (dữ liệu cũ trước khi có tính năng này).
ALTER TABLE loan_requests
    ADD COLUMN repayment_day TINYINT NULL COMMENT 'Ngày trả nợ hàng tháng: 5 hoặc 20' AFTER term_months;
