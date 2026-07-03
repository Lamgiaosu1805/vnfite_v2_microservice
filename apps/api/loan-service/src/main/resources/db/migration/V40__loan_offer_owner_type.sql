-- V40: Tư cách đầu tư của lệnh đầu tư — cá nhân hay doanh nghiệp.
--
-- Nhà đầu tư (theo mô hình "1 tài khoản 2 tư cách") có thể đầu tư dưới danh nghĩa cá nhân
-- hoặc doanh nghiệp. owner_type quyết định ví nào bị khóa/trừ/hoàn/cộng cho toàn bộ vòng đời
-- lệnh đầu tư đó: PERSONAL → ví cá nhân, BUSINESS → ví doanh nghiệp.
--
-- Lệnh cũ mặc định PERSONAL → tương thích ngược, không đổi luồng đầu tư cá nhân hiện tại.
ALTER TABLE loan_offers
    ADD COLUMN owner_type VARCHAR(20) NOT NULL DEFAULT 'PERSONAL' AFTER investor_id;

-- Bản ghi cộng hoàn trả bị lỗi chờ đối soát cũng phải nhớ tư cách để retry cộng đúng ví.
ALTER TABLE pending_investor_credit
    ADD COLUMN owner_type VARCHAR(20) NOT NULL DEFAULT 'PERSONAL' AFTER offer_id;
