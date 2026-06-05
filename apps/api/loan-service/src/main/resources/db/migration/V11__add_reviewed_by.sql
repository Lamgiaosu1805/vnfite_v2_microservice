-- V11: Lưu tên ban lãnh đạo phê duyệt / từ chối vào loan_requests.
-- Trước đây chỉ có reviewed_at, thiếu reviewed_by nên không biết ai đã duyệt.
ALTER TABLE loan_requests
    ADD COLUMN reviewed_by VARCHAR(100) NULL AFTER reviewed_at;
