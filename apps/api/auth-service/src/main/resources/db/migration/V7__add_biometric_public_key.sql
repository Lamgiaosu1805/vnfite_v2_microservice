-- Đăng nhập sinh trắc học theo chuẩn challenge–response (asymmetric crypto).
-- Thiết bị tạo cặp khóa trong Secure Enclave / Android Keystore, private key không rời máy.
-- Server chỉ lưu PUBLIC key để verify chữ ký challenge — không lưu secret nào.
-- Lưu ở DB (không phải Redis) vì đây là credential lâu dài, phải tồn tại qua mọi restart.
ALTER TABLE users
    ADD COLUMN biometric_public_key TEXT NULL
    COMMENT 'Public key (base64 X.509 SPKI) cho đăng nhập sinh trắc học; null = chưa bật/đã tắt';
