-- Biểu lãi suất gọi vốn (QĐ .../QĐ-LSGV/21): lãi suất & phí giải ngân theo
-- (Nhóm sản phẩm × Hạng tín nhiệm). Nhóm sản phẩm quyết định biểu áp dụng.
--
--   Nhóm 1: SPGV01 siêu tốc, SPGV02 sinh viên
--   Nhóm 2: SPGV03–14 (hoá đơn, tiêu dùng, bảo hiểm, thẻ tín dụng, công nhân viên, tài xế, thẩm mỹ...)
--   Nhóm 3: SPGV15 bác sĩ, SPGV16 giảng viên/giáo viên, SPGV17 cán bộ LLVT
--   Nhóm 4: SPGV18 hộ KD tiểu thương, SPGV19 hộ KD chợ/TTTM, SPGV20 DN vừa và nhỏ
ALTER TABLE loan_products
    ADD COLUMN product_group INT NOT NULL DEFAULT 2 AFTER category;

-- profession_bound = sản phẩm ràng buộc theo nghề/đối tượng (bác sĩ, giáo viên, sinh viên...).
-- Khi true: nghề nghiệp được xác định bởi sản phẩm, thẩm định cần BẰNG CHỨNG đúng đối tượng
-- thay vì nhập nghề tự do.
ALTER TABLE loan_products
    ADD COLUMN profession_bound TINYINT(1) NOT NULL DEFAULT 0 AFTER product_group;
