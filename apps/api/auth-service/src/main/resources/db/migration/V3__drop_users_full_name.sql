-- full_name trên users là bản sao dư thừa của kyc_submissions.full_name
-- Source of truth duy nhất là kyc_submissions — xóa khỏi users
ALTER TABLE users DROP COLUMN full_name;
