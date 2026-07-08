package com.p2plending.loan.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class LoanCreateRequest {

    /**
     * Mã sản phẩm gọi vốn: FAST | STUDENT | CONSUMER | COSMETIC
     * Bắt buộc — xác định loại sản phẩm và ràng buộc về số tiền, kỳ hạn.
     */
    @NotBlank(message = "Mã sản phẩm gọi vốn là bắt buộc (productCode)")
    @Size(max = 50)
    private String productCode;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "1000.00", message = "Minimum loan amount is 1,000")
    @DecimalMax(value = "2000000000.00", message = "Maximum funding amount is 2,000,000,000")
    @Digits(integer = 13, fraction = 2)
    private BigDecimal amount;

    @NotNull(message = "Term is required")
    @Min(value = 1,   message = "Minimum term is 1 month")
    @Max(value = 360, message = "Maximum term is 360 months (30 years)")
    private Integer termMonths;

    @NotBlank(message = "Purpose is required")
    @Size(min = 10, max = 500, message = "Purpose must be between 10 and 500 characters")
    private String purpose;

    @Size(max = 100)
    private String referredBy;

    @Size(max = 100)
    private String ref1FullName;

    @Size(max = 50)
    private String ref1Relationship;

    @Pattern(regexp = "^(0|\\+84)[0-9]{8,10}$", message = "Số điện thoại người tham chiếu 1 không hợp lệ")
    @Size(max = 20)
    private String ref1Phone;

    @Size(max = 500)
    private String ref1Address;

    @Size(max = 100)
    private String ref2FullName;

    @Size(max = 50)
    private String ref2Relationship;

    @Pattern(regexp = "^(0|\\+84)[0-9]{8,10}$", message = "Số điện thoại người tham chiếu 2 không hợp lệ")
    @Size(max = 20)
    private String ref2Phone;

    @Size(max = 500)
    private String ref2Address;

    @DecimalMin(value = "0", inclusive = false, message = "Monthly income must be positive")
    @Digits(integer = 13, fraction = 2)
    private BigDecimal monthlyIncome;

    @Size(max = 100)
    private String occupation;

    @Size(max = 255)
    private String workplace;

    @Size(max = 500)
    private String workplaceAddress;

    @Size(max = 500)
    private String currentAddress;

    /** Xã / Phường / Thị trấn — lưu riêng, không được tự ghép vào currentAddress. */
    @Size(max = 200)
    private String commune;

    /** Tỉnh / Thành phố — theo NQ 202/2025/QH15. */
    @Size(max = 100)
    private String province;

    /**
     * Ngày trả nợ hàng tháng: chỉ được chọn 5 hoặc 20.
     * Bắt buộc khi tạo hồ sơ — dùng để tính kỳ đầu (pro-rated) và lịch trả nợ.
     */
    @NotNull(message = "Vui lòng chọn ngày trả nợ hàng tháng (ngày 5 hoặc ngày 20)")
    private Integer repaymentDay;

    /** Danh sách chứng từ đính kèm (tùy chọn). fileId lấy từ file-manager sau khi upload. */
    private List<LoanDocumentInput> documents;
}
