-- ============================================================
-- V4: Loan Products catalog
-- 4 sản phẩm gọi vốn cá nhân ban đầu (có thể mở rộng sau)
-- ============================================================

CREATE TABLE IF NOT EXISTS loan_products (
  id                VARCHAR(36)    NOT NULL PRIMARY KEY,
  code              VARCHAR(50)    NOT NULL UNIQUE,
  name              VARCHAR(200)   NOT NULL,
  category          ENUM('INDIVIDUAL','BUSINESS') NOT NULL DEFAULT 'INDIVIDUAL',
  description       TEXT           NULL,
  min_amount        DECIMAL(15,2)  NOT NULL,
  max_amount        DECIMAL(15,2)  NOT NULL,
  -- Danh sách kỳ hạn cho phép, lưu dạng "1,3,6,12" — parse ở tầng service
  available_terms   VARCHAR(100)   NOT NULL,
  -- Lãi suất tối đa (%/năm) — NULL = không giới hạn cứng, CMS tự quyết
  max_interest_rate DECIMAL(5,2)   NULL,
  -- Lãi phạt trả chậm tính theo % của lãi suất hiện tại
  late_fee_rate     DECIMAL(5,2)   NOT NULL DEFAULT 150.00,
  -- URL ảnh đại diện sản phẩm hiển thị trên app
  image_url         VARCHAR(500)   NULL,
  is_active         TINYINT(1)     NOT NULL DEFAULT 1,
  is_deleted        TINYINT(1)     NOT NULL DEFAULT 0,
  sort_order        INT            NOT NULL DEFAULT 0,
  created_at        DATETIME       DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_product_code   (code),
  KEY idx_product_active (is_active)
);

-- Thêm product_id vào loan_requests — nullable để không break dữ liệu cũ
ALTER TABLE loan_requests
  ADD COLUMN product_id VARCHAR(36) NULL AFTER borrower_id,
  ADD KEY idx_loan_product (product_id);
