package com.p2plending.loan.domain.enums;

/**
 * Danh mục nghề nghiệp chuẩn — theo phân loại các ngân hàng VN dùng trong hồ sơ tín dụng.
 * Dùng cho dropdown chọn nghề nghiệp (thay nhập tự do) trên app gọi vốn.
 */
public enum OccupationCategory {
    CIVIL_SERVANT      ("Cán bộ, công chức, viên chức nhà nước"),
    ARMED_FORCES       ("Quân đội, công an, lực lượng vũ trang"),
    OFFICE_STAFF       ("Nhân viên văn phòng"),
    FACTORY_WORKER     ("Công nhân"),
    TEACHER            ("Giáo viên, giảng viên"),
    HEALTHCARE         ("Y, bác sĩ, nhân viên y tế"),
    MANAGER            ("Lãnh đạo, quản lý"),
    BUSINESS_OWNER     ("Chủ doanh nghiệp"),
    HOUSEHOLD_BUSINESS ("Hộ kinh doanh, tiểu thương"),
    SELF_EMPLOYED      ("Kinh doanh tự do, buôn bán nhỏ"),
    SALES              ("Nhân viên kinh doanh, bán hàng"),
    ENGINEER           ("Kỹ sư, kỹ thuật viên"),
    PROFESSIONAL       ("Nghề chuyên môn (luật sư, kế toán, kiểm toán...)"),
    SERVICE_WORKER     ("Lao động ngành dịch vụ (nhà hàng, khách sạn, bán lẻ)"),
    DRIVER             ("Tài xế"),
    FREELANCER         ("Lao động tự do"),
    MANUAL_LABOR       ("Lao động phổ thông"),
    FARMER             ("Nông, lâm, ngư nghiệp"),
    HOMEMAKER          ("Nội trợ"),
    RETIRED            ("Hưu trí"),
    STUDENT            ("Sinh viên, học sinh"),
    OTHER              ("Khác");

    private final String label;

    OccupationCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
