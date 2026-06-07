-- Thêm reference_id để liên kết thông báo với đối tượng nghiệp vụ (loanId, userId, v.v.)
ALTER TABLE notifications
    ADD COLUMN reference_id VARCHAR(36) NULL COMMENT 'ID của đối tượng liên quan (loanId, userId...)' AFTER channel,
    ADD COLUMN reference_type VARCHAR(50) NULL COMMENT 'Loại đối tượng: LOAN, USER, PAYMENT...' AFTER reference_id;

CREATE INDEX idx_notifications_reference ON notifications (reference_type, reference_id);
