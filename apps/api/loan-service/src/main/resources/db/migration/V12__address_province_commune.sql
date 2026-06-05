-- V12: Tách địa chỉ thành các cột riêng để lọc/thống kê theo tỉnh/thành phố.
-- Trước đây toàn bộ địa chỉ dồn vào current_address (VARCHAR 500), mất khả năng
-- filter theo tỉnh hoặc xã. Sau migration này:
--   current_address  = địa chỉ số nhà + tên đường (phần tự do)
--   commune          = xã / phường / thị trấn
--   province         = tỉnh / thành phố (theo NQ 202/2025/QH15, 34 đơn vị)

ALTER TABLE loan_requests
    ADD COLUMN commune  VARCHAR(200) NULL AFTER current_address,
    ADD COLUMN province VARCHAR(100) NULL AFTER commune;

-- Index để tìm kiếm/thống kê theo tỉnh nhanh
CREATE INDEX idx_loan_province ON loan_requests (province);
