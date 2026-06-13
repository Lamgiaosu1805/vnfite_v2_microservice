-- Bỏ total_balance khỏi wallets — số dư thực luôn lấy trực tiếp từ TIKLUY (VNF_ACCOUNT_MANAGEMENT.TOTAL_MONEY)
ALTER TABLE wallets DROP COLUMN total_balance;
