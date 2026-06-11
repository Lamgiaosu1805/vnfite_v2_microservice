-- Lưu fileId của file-manager để admin xem lại chứng từ gốc qua
-- GET service.vnfite.com.vn/file-manager/v2/file/{file_id}
ALTER TABLE document_analyses
    ADD COLUMN file_id VARCHAR(100) NULL COMMENT 'ID file gốc trên file-manager' AFTER file_name;
