-- ============================================================
-- Migration: user 0343316951 từ hệ thống cũ sang hệ thống mới
-- Chạy trên server 118, kết nối host MySQL 127.0.0.1
-- Thực hiện theo thứ tự: auth_db trước, payment_db sau
-- ============================================================

-- ── 1. AUTH_DB ───────────────────────────────────────────────

USE auth_db;

-- 1.1 User
INSERT INTO users (
    id, phone, password, email,
    kyc_status, referred_by, is_deleted, created_at, updated_at
) VALUES (
    '981bef75-2dd3-4607-a7ec-a39624e9e961',
    '0343316951',
    '$2a$10$9O1NukVfSJWPU3DJnPSOV.rwHNmwYSLl9ACIRVFWaT09qaRHlGPlW',
    NULL,
    'APPROVED',
    NULL,
    0,
    '2026-02-26 14:54:21',
    NOW()
) ON DUPLICATE KEY UPDATE
    kyc_status  = VALUES(kyc_status),
    updated_at  = NOW();

-- 1.2 KYC submission
INSERT INTO kyc_submissions (
    id, user_id,
    cccd_number, full_name, date_of_birth,
    permanent_address, hometown,
    issue_date, issuing_authority, expiry_date,
    front_image_id, back_image_id, portrait_image_id,
    status, is_deleted, created_at, updated_at
) VALUES (
    '991ac9b5-7f76-4133-9354-3b582f7aaae5',
    '981bef75-2dd3-4607-a7ec-a39624e9e961',
    '034204005607',
    'BÙI VĂN NGHIÊM',
    '2004-01-22',
    'Phong Lâm, Thủy Phong, Thái Thụy, Thái Bình',
    'Phong Lâm, Thủy Phong, Thái Thụy, Thái Bình',
    '2021-04-25',
    'Cục trưởng Cục Cảnh sát quản lý hành chính về trật tự xã hội',
    '2029-01-22',
    '699ffe11488d7d3a91cbd2c0',
    '699ffe11488d7d3a91cbd2c2',
    '699ffe11488d7d3a91cbd2c4',
    'APPROVED',
    0,
    '2026-02-26 15:02:25',
    NOW()
) ON DUPLICATE KEY UPDATE
    status     = VALUES(status),
    updated_at = NOW();

-- 1.3 FCM token (thiết bị active nhất — IS_DELETED=N)
INSERT INTO user_fcm_tokens (user_id, fcm_token, device_key, updated_at)
VALUES (
    '981bef75-2dd3-4607-a7ec-a39624e9e961',
    'chctZvrPNkWRqI0Daa6PK1:APA91bFXeDrJL43JmUspzjD4NWyv803jwqLqyVOgJZ7r6dMz_bk5hdu_DbGhHwZqs5Fy_MoGQb_cHX0QaMO2tUNv7dPq8-ZHpZHATqculEi5EM5mupZpe-g',
    'D93C757D-8459-4B11-BDAC-4B7BCA18D95E',
    NOW()
) ON DUPLICATE KEY UPDATE
    fcm_token  = VALUES(fcm_token),
    device_key = VALUES(device_key),
    updated_at = NOW();


-- ── 2. PAYMENT_DB ────────────────────────────────────────────

USE payment_db;

-- 2.1 Wallet (balance lấy từ TIKLUY qua vnf_account_no, không lưu local)
INSERT INTO wallets (
    id, user_id, vnf_account_no, locked_balance, is_deleted, created_at, updated_at
) VALUES (
    'c5608da5-1b1c-4b79-935c-adf14e0f818c',
    '981bef75-2dd3-4607-a7ec-a39624e9e961',
    'VNF0000009267',
    0.00,
    0,
    '2026-02-26 15:02:26',
    NOW()
) ON DUPLICATE KEY UPDATE
    updated_at = NOW();

-- 2.2 Ngân hàng liên kết (TPBank)
INSERT INTO linked_banks (
    id, user_id, bank_code, bank_name,
    bank_account_no, account_name, is_default, is_deleted, created_at, updated_at
) VALUES (
    '2a2c2d17-86b5-4578-998e-2c19a0dbccc0',
    '981bef75-2dd3-4607-a7ec-a39624e9e961',
    'TPB',
    'TPBank',
    '00006129521',
    'BUI VAN NGHIEM',
    1,
    0,
    '2026-03-09 16:58:51',
    NOW()
) ON DUPLICATE KEY UPDATE
    updated_at = NOW();

-- 2.3 Lịch sử giao dịch ví
-- Txn 1: Nạp tự động MB 101,227,397 VND (2026-04-09)
INSERT INTO wallet_transactions (
    id, wallet_id, type, amount, status,
    reference_id, external_ref, description, balance_after,
    is_deleted, created_at, updated_at
) VALUES (
    'a5631c96-ab51-4236-8245-b755e68ad5fa',
    'c5608da5-1b1c-4b79-935c-adf14e0f818c',
    'DEPOSIT',
    101227397.00,
    'SUCCESS',
    'SML26040911150099287897040760991199287840181500',
    'FT26099173084229',
    'Nạp tiền tự động MB - 101,227,397 VND',
    101227397.00,
    0,
    '2026-04-09 18:15:02',
    '2026-04-09 18:15:02'
) ON DUPLICATE KEY UPDATE updated_at = NOW();

-- Txn 2: Thanh toán khoản vay VNF010707 (2026-04-09)
INSERT INTO wallet_transactions (
    id, wallet_id, type, amount, status,
    reference_id, external_ref, description, balance_after,
    is_deleted, created_at, updated_at
) VALUES (
    '24212259-89d3-4ca5-96da-5541ff4daa6b',
    'c5608da5-1b1c-4b79-935c-adf14e0f818c',
    'WITHDRAW',
    101227397.00,
    'SUCCESS',
    'TNTD_0001_VNFITE',
    NULL,
    'Khoản vay VNF010707 đã được thanh toán - 101,227,397 VND',
    0.00,
    0,
    '2026-04-09 20:00:00',
    '2026-04-09 20:00:00'
) ON DUPLICATE KEY UPDATE updated_at = NOW();

-- Txn 3: Nạp tự động MB 50,000 VND (2026-06-16 lần 1)
INSERT INTO wallet_transactions (
    id, wallet_id, type, amount, status,
    reference_id, external_ref, description, balance_after,
    is_deleted, created_at, updated_at
) VALUES (
    '31356ccd-18c0-47a9-8a43-30b2ae7a27f1',
    'c5608da5-1b1c-4b79-935c-adf14e0f818c',
    'DEPOSIT',
    50000.00,
    'SUCCESS',
    'SML26061610122537739297042361671037739200000001',
    'FT26167700519521',
    'Nạp tiền tự động MB - 50,000 VND',
    50000.00,
    0,
    '2026-06-16 17:12:26',
    '2026-06-16 17:12:26'
) ON DUPLICATE KEY UPDATE updated_at = NOW();

-- Txn 4: Nạp tự động MB 50,000 VND (2026-06-16 lần 2)
INSERT INTO wallet_transactions (
    id, wallet_id, type, amount, status,
    reference_id, external_ref, description, balance_after,
    is_deleted, created_at, updated_at
) VALUES (
    '7f95f682-62bd-4508-844c-c6768baa0f72',
    'c5608da5-1b1c-4b79-935c-adf14e0f818c',
    'DEPOSIT',
    50000.00,
    'SUCCESS',
    'SML26061610202645428397042361671045428300000001',
    'FT26167050764329',
    'Nạp tiền tự động MB - 50,000 VND',
    100000.00,
    0,
    '2026-06-16 17:20:27',
    '2026-06-16 17:20:27'
) ON DUPLICATE KEY UPDATE updated_at = NOW();
