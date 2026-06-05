package com.p2plending.loan.domain.enums;

/** Kiểu trả nợ — thuộc tính của sản phẩm gọi vốn. */
public enum RepaymentMethod {
    /** Gốc + lãi trả đều mỗi kỳ (annuity / EMI). Mặc định cho sản phẩm cá nhân. */
    EMI_MONTHLY,
    /** Lãi trả đều hàng tháng, gốc trả theo quý — dành cho hộ kinh doanh / doanh nghiệp. */
    INTEREST_MONTHLY_PRINCIPAL_QUARTERLY
}
