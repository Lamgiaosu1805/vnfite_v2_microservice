-- Dữ liệu cũ đã có prefix /images; migration import trước đây cộng thêm lần nữa.
-- Chuẩn public URL qua Nginx: /images/news/<file>.
UPDATE news
SET image_url = CONCAT('/images/', SUBSTRING(image_url, CHAR_LENGTH('/images/images/') + 1))
WHERE image_url LIKE '/images/images/%';
