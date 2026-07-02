package com.p2plending.auth.domain.enums;

/** Loại hình đăng ký kinh doanh của hồ sơ doanh nghiệp. */
public enum BusinessType {
    /** Hộ kinh doanh cá thể — GCN đăng ký hộ kinh doanh, MST có thể chưa có. */
    HOUSEHOLD,
    /** Công ty (TNHH, cổ phần...) — GCN đăng ký doanh nghiệp + MST. */
    COMPANY
}
