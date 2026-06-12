-- V9: CIC manual lookup
-- Lưu kết quả tra cứu CIC nhập tay khi chưa có API CIC sandbox NĐ94.
-- Thẩm định viên tra CIC bên ngoài rồi nhập vào CMS; bản ghi này vừa là nguồn
-- chấm điểm nhóm B (gửi sang credit-service) vừa là audit trail tuân thủ
-- (ai nhập, khi nào, ngày tra cứu, consent NĐ13).
-- Dữ liệu ops nội bộ CMS → đặt trong cms_db (không mirror từ service khác).

CREATE TABLE cic_manual_lookups
(
    id                 VARCHAR(36)  NOT NULL PRIMARY KEY,
    loan_id            VARCHAR(36)  NOT NULL,
    borrower_id        VARCHAR(36),

    -- Kết quả tra CIC (chuẩn hóa để chấm nhóm B)
    debt_group         INT          NOT NULL,            -- 1-5: nhóm nợ cao nhất hiện tại (B1)
    max_dpd            INT,                               -- ngày quá hạn cao nhất 12 tháng (B2)
    active_lenders     INT,                               -- số TCTD đang có dư nợ (B4)
    total_outstanding  DECIMAL(15, 2),                    -- tổng dư nợ hiện tại (tham khảo)
    inquiries_recent   INT,                               -- số lần hỏi tin gần đây (B7, tham khảo)

    -- Hiệu lực & chứng cứ
    checked_at         DATE         NOT NULL,             -- ngày tra cứu CIC
    attachment_file_id VARCHAR(100),                      -- file kết quả CIC (tùy chọn)
    note               VARCHAR(1000),
    consent_confirmed  TINYINT(1)   NOT NULL DEFAULT 0,   -- xác nhận có consent tra cứu (NĐ13)

    -- Audit
    entered_by         VARCHAR(100) NOT NULL,             -- cms admin nhập

    is_deleted         TINYINT(1)   NOT NULL DEFAULT 0,
    created_at         DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_cic_loan (loan_id),
    INDEX idx_cic_checked_at (checked_at)
);
