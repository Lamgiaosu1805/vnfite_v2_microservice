-- V37: Phí tất toán trước hạn theo hợp đồng — thêm ngưỡng miễn phí + mức sàn.
--
-- Theo Hợp đồng Hợp tác Đầu tư (Phụ Lục 01):
--   • Thời gian sử dụng vốn < 2/3 kỳ hạn → phí = early_settlement_fee_rate% × gốc còn lại,
--     nhưng KHÔNG thấp hơn mức sàn early_settlement_min_fee (mặc định 500.000đ).
--   • Thời gian sử dụng vốn >= 2/3 kỳ hạn → MIỄN PHÍ (phí = 0).
--
-- early_settlement_free_ratio: tỷ lệ kỳ hạn đã dùng để được miễn phí (mặc định 2/3 = 0.6667)
-- early_settlement_min_fee:    mức phí tối thiểu (VND) khi phí áp dụng (mặc định 500.000)
ALTER TABLE loan_products
    ADD COLUMN early_settlement_free_ratio DECIMAL(5,4)  NOT NULL DEFAULT 0.6667 AFTER early_settlement_fee_rate,
    ADD COLUMN early_settlement_min_fee    DECIMAL(15,2) NOT NULL DEFAULT 500000 AFTER early_settlement_free_ratio;
