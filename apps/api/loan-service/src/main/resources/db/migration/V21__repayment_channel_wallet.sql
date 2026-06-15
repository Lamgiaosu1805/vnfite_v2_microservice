-- Bổ sung kênh trả nợ từ ví VNFITE: WALLET (người gọi vốn chủ động trả trên app)
-- và AUTO_DEBIT (hệ thống tự động trừ ví vào ngày đến hạn).
-- Cột channel đang là ENUM nên BẮT BUỘC mở rộng danh sách giá trị, nếu không
-- insert 'WALLET'/'AUTO_DEBIT' sẽ bị MySQL từ chối (Error 1265 ở strict mode).
-- Không đụng dữ liệu hiện có — chỉ mở rộng tập giá trị hợp lệ.
ALTER TABLE repayment_transaction
  MODIFY COLUMN channel ENUM('COLLECTION_PARTNER','MANUAL_ADMIN','WALLET','AUTO_DEBIT')
  NOT NULL DEFAULT 'MANUAL_ADMIN';
