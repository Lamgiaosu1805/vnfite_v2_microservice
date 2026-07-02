package com.p2plending.credit.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Phân tích Giấy chứng nhận đăng ký kinh doanh (GPKD) của hồ sơ doanh nghiệp —
 * trích xuất thông tin + đối chiếu với thông tin người dùng khai báo và eKYC.
 * Kết quả chỉ tham khảo cho admin thẩm định, không auto-approve.
 */
@Data
public class AnalyzeBusinessLicenseRequest {

    @NotBlank(message = "userId là bắt buộc")
    private String userId;

    @NotBlank(message = "fileId là bắt buộc")
    private String fileId;

    // ── Thông tin khai báo để AI đối chiếu ──
    private String expectedBusinessName;
    private String expectedRegistrationNumber;
    private String expectedTaxCode;
    /** Tên người đại diện khai báo — phải khớp tên trên GPKD và tên eKYC. */
    private String expectedRepresentativeName;
    private String expectedRepresentativeCccd;
    /** HOUSEHOLD (hộ kinh doanh) | COMPANY (công ty). */
    private String expectedBusinessType;
}
