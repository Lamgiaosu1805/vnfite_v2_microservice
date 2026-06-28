-- Các khoản cũ lưu tên nơi làm việc vào occupation thay vì workplace.
-- Copy sang workplace để hiển thị đúng trên app.
UPDATE loan_requests
SET workplace = occupation
WHERE workplace IS NULL
  AND occupation IS NOT NULL;
