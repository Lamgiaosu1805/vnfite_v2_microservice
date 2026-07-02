package com.p2plending.auth.domain.enums;

/**
 * Tier tài khoản sau khi hồ sơ doanh nghiệp được duyệt. Chỉ là tier HIỂN THỊ/mở khóa —
 * không giới hạn quyền cá nhân: user BUSINESS/ENTERPRISE vẫn gọi vốn/đầu tư cá nhân bình thường.
 */
public enum AccountType {
    INDIVIDUAL,
    /** Hộ kinh doanh cá thể đã được duyệt. */
    BUSINESS,
    /** Công ty (TNHH/CP...) đã được duyệt. */
    ENTERPRISE
}
