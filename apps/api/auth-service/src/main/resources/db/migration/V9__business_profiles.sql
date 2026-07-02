-- V9: Hồ sơ doanh nghiệp — nâng cấp tài khoản cá nhân thành tư cách doanh nghiệp.
--
-- Mô hình "1 tài khoản – 2 tư cách": tài khoản + eKYC cá nhân giữ nguyên, hồ sơ doanh nghiệp
-- là lớp xác minh BỔ SUNG (không thay thế). Người dùng vẫn gọi vốn/đầu tư cá nhân bình thường;
-- hồ sơ được duyệt chỉ MỞ THÊM nhóm sản phẩm BUSINESS/ENTERPRISE.
--
-- account_type: tier hiển thị sau duyệt — Hộ kinh doanh → BUSINESS, Công ty → ENTERPRISE.
-- Duyệt TAY trên CMS (khác eKYC cá nhân tự động duyệt qua VNPT liveness).

DROP PROCEDURE IF EXISTS add_user_account_type_column;

DELIMITER //
CREATE PROCEDURE add_user_account_type_column()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'account_type'
    ) THEN
        ALTER TABLE users ADD COLUMN account_type VARCHAR(20) NOT NULL DEFAULT 'INDIVIDUAL';
    END IF;
END//
DELIMITER ;

CALL add_user_account_type_column();

DROP PROCEDURE add_user_account_type_column;

CREATE TABLE IF NOT EXISTS business_profiles (
    id                      VARCHAR(36)  NOT NULL,
    user_id                 VARCHAR(36)  NOT NULL,
    business_type           VARCHAR(20)  NOT NULL COMMENT 'HOUSEHOLD (hộ kinh doanh) | COMPANY (công ty)',
    business_name           VARCHAR(255) NOT NULL,
    registration_number     VARCHAR(50)  NOT NULL COMMENT 'Số GCN ĐKKD / GCN đăng ký hộ kinh doanh',
    tax_code                VARCHAR(20)  NULL COMMENT 'MST — hộ kinh doanh có thể chưa có',
    issue_date              DATE         NULL,
    issued_by               VARCHAR(255) NULL COMMENT 'Nơi cấp GCN',
    head_office_address     VARCHAR(500) NOT NULL,
    business_sector         VARCHAR(255) NULL COMMENT 'Ngành nghề kinh doanh chính',
    representative_name     VARCHAR(100) NOT NULL COMMENT 'Người đại diện pháp luật / chủ hộ',
    representative_cccd     VARCHAR(20)  NOT NULL COMMENT 'CCCD người đại diện — đối chiếu eKYC',
    license_image_id        VARCHAR(255) NOT NULL COMMENT 'Ảnh GPKD trang chính',
    license_extra1_image_id VARCHAR(255) NULL,
    license_extra2_image_id VARCHAR(255) NULL,
    status                  VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING | APPROVED | REJECTED',
    reject_reason           VARCHAR(500) NULL,
    ai_verdict              VARCHAR(30)  NULL COMMENT 'CONSISTENT | SUSPICIOUS | UNREADABLE — chỉ tham khảo',
    ai_summary              TEXT         NULL,
    reviewed_by             VARCHAR(100) NULL,
    reviewed_at             DATETIME     NULL,
    is_deleted              TINYINT(1)   NOT NULL DEFAULT 0,
    created_at              DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_business_profiles_user (user_id),
    KEY idx_business_profiles_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
