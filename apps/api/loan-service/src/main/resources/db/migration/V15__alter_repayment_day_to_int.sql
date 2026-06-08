-- Align repayment_day column type with Hibernate Integer mapping.
-- Existing business values remain 5 or 20; only the SQL type changes to avoid startup failure.
ALTER TABLE loan_requests
    MODIFY COLUMN repayment_day INT NULL COMMENT 'Ngày trả nợ hàng tháng: 5 hoặc 20';
