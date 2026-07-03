-- V39: Snapshot định danh Bên A (người gọi vốn) vào khế ước nhận nợ.
--
-- Với khoản gọi vốn sản phẩm doanh nghiệp (BUSINESS/ENTERPRISE), người gọi vốn ký với tư cách
-- PHÁP NHÂN: hợp đồng phải ghi tên doanh nghiệp + số ĐKKD + người đại diện, thay vì tên cá nhân.
-- Các field snapshot tại thời điểm phát hành khế ước để hợp đồng bất biến kể cả khi hồ sơ DN đổi sau.
--
-- Khoản cá nhân và hợp đồng đầu tư của nhà đầu tư để NULL (mặc định tư cách cá nhân) → tương thích ngược.
ALTER TABLE loan_contracts
    ADD COLUMN party_type           VARCHAR(20)  NULL AFTER party_id,
    ADD COLUMN party_name           VARCHAR(200) NULL AFTER party_type,
    ADD COLUMN party_identity_no    VARCHAR(50)  NULL AFTER party_name,
    ADD COLUMN party_representative VARCHAR(150) NULL AFTER party_identity_no;
