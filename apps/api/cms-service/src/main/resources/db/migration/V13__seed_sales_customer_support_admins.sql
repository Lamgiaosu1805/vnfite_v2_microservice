-- Seed CMS accounts for VNFITE sales/customer-support staff.
-- Idempotent: existing accounts keep their current password.

INSERT INTO cms_admin_users (
    id, username, email, full_name, password, must_change_password,
    role, active, is_deleted, created_at, updated_at
) VALUES
    ('04dd1e7d-0b9c-4b9d-bdd5-4a0cbc915b9f', 'huyenhvt', 'huyenhvt@vnfite.com.vn', 'Hà Vũ Thanh Huyền', '$2y$10$IkAHEXxRcMndVkjQQ7ocGeCLHEHXAa2QU3zrIFgW3R0kV.mZawSz6', 1, 'CUSTOMER_SUPPORT', 1, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('08df2c5d-78d9-4cc9-805d-0048f5d6f736', 'hoangnh', 'hoangnh@vnfite.com.vn', 'Nguyễn Huy Hoàng', '$2y$10$Xphl7Hs1UKy3cCeYzpfUVuVF8aPkh5dMC7Ee7xyDr7GaiFCyTLMoe', 1, 'CUSTOMER_SUPPORT', 1, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('e1d9e58d-d417-43a5-8d76-267004406aa1', 'anhhv', 'anhhv@vnfite.com.vn', 'Hoàng Vân Anh', '$2y$10$I35tQUEOPNI5kOv3Kwoge.Me/OTsAFDLhB.1FL8Gbk30/YiOvPNaS', 1, 'CUSTOMER_SUPPORT', 1, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('d5feb7e2-5ee2-4f1d-abd4-81ca8b907688', 'tuandt', 'tuandt@vnfite.com.vn', 'Đặng Trần Tuấn', '$2y$10$NluV9v9.6b6SdFue4eIWqeAUwTYmm04CuTpxbccvhOpKIwhkLM2La', 1, 'CUSTOMER_SUPPORT', 1, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('d023eac8-d62f-40a6-b241-538701b6244b', 'quantm', 'quantm@vnfite.com.vn', 'Trình Minh Quân', '$2y$10$YSW/vxwvIsVlUINEu8bu0uujBN8UyK8YPefB.1C8O02LIgIvtTuZi', 1, 'CUSTOMER_SUPPORT', 1, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('3b749d68-564d-4c1b-b486-def6d6708507', 'chunglv', 'chunglv@vnfite.com.vn', 'Lê Văn Chung', '$2y$10$3pUFahILNj034d2QUrtNUOaNNTHYAG.4RuMP2SY86u48IbwKOXys2', 1, 'CUSTOMER_SUPPORT', 1, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON DUPLICATE KEY UPDATE
    full_name = VALUES(full_name),
    role = 'CUSTOMER_SUPPORT',
    active = 1,
    is_deleted = 0,
    updated_at = CURRENT_TIMESTAMP;

INSERT IGNORE INTO cms_admin_user_roles (user_id, role)
SELECT id, 'CUSTOMER_SUPPORT'
FROM cms_admin_users
WHERE email IN (
    'huyenhvt@vnfite.com.vn',
    'hoangnh@vnfite.com.vn',
    'anhhv@vnfite.com.vn',
    'tuandt@vnfite.com.vn',
    'quantm@vnfite.com.vn',
    'chunglv@vnfite.com.vn'
) AND is_deleted = 0;

INSERT IGNORE INTO cms_admin_user_roles (user_id, role)
SELECT id, 'OPS'
FROM cms_admin_users
WHERE email IN (
    'huyenhvt@vnfite.com.vn',
    'hoangnh@vnfite.com.vn',
    'anhhv@vnfite.com.vn',
    'tuandt@vnfite.com.vn',
    'quantm@vnfite.com.vn',
    'chunglv@vnfite.com.vn'
) AND is_deleted = 0;
