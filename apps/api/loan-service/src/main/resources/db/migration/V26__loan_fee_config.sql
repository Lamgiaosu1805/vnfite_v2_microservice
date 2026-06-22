-- Bảng cấu hình phí — mỗi loại phí là một dòng (hiện tại chỉ có APPRAISAL).
-- Admin CMS cấu hình qua UI, lưu ở đây.
CREATE TABLE IF NOT EXISTS loan_fee_config (
    id          INT            NOT NULL AUTO_INCREMENT,
    fee_type    VARCHAR(50)    NOT NULL COMMENT 'APPRAISAL | ...',
    fee_name    VARCHAR(100)   NOT NULL COMMENT 'Tên hiển thị',
    fee_amount  DECIMAL(15,2)  NOT NULL DEFAULT 0 COMMENT 'Giá trị phí (VNĐ hoặc %)',
    calc_type   ENUM('FIXED','PERCENTAGE') NOT NULL DEFAULT 'FIXED'
                    COMMENT 'FIXED=số tiền cố định, PERCENTAGE=% số tiền vay',
    vat_rate    DECIMAL(5,4)   NOT NULL DEFAULT 0.1000 COMMENT 'Thuế VAT (mặc định 10%)',
    is_active   TINYINT(1)     NOT NULL DEFAULT 1,
    updated_by  VARCHAR(100)   NULL,
    created_at  DATETIME       DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_fee_type (fee_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Seed phí thẩm định mặc định (amount = 0 — CMS sẽ cập nhật)
INSERT INTO loan_fee_config (fee_type, fee_name, fee_amount, calc_type, vat_rate, is_active)
VALUES ('APPRAISAL', 'Phí thẩm định hồ sơ', 0, 'FIXED', 0.1000, 1)
ON DUPLICATE KEY UPDATE fee_type = fee_type;

-- Thêm cột phí vào loan_requests — ghi lại giá trị tại thời điểm giải ngân
ALTER TABLE loan_requests
    ADD COLUMN appraisal_fee     DECIMAL(15,2) NULL AFTER disbursed_by
        COMMENT 'Phí thẩm định hồ sơ áp dụng khi giải ngân',
    ADD COLUMN vat_amount        DECIMAL(15,2) NULL AFTER appraisal_fee
        COMMENT 'Thuế VAT = vat_rate × tổng phí',
    ADD COLUMN total_fee         DECIMAL(15,2) NULL AFTER vat_amount
        COMMENT 'Tổng phí = appraisal_fee + vat_amount',
    ADD COLUMN net_disbursement  DECIMAL(15,2) NULL AFTER total_fee
        COMMENT 'Số tiền thực nhận = amount - total_fee';
