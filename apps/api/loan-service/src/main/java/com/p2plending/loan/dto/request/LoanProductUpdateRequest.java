package com.p2plending.loan.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class LoanProductUpdateRequest {

    @NotBlank
    private String name;

    private String description;

    @NotNull
    @DecimalMin("100000")
    private BigDecimal minAmount;

    @NotNull
    @DecimalMin("100000")
    private BigDecimal maxAmount;

    /** Danh sách kỳ hạn (tháng), ví dụ [1, 3, 6, 12]. */
    @NotNull
    private List<Integer> availableTerms;

    /** Lãi suất tối đa (%/năm). Null = không giới hạn cứng. */
    private BigDecimal maxInterestRate;

    /** Lãi phạt GỐC quá hạn (% của lãi suất). */
    private BigDecimal lateFeeRate;

    /** Phí phạt LÃI quá hạn (%/năm trên lãi chưa trả). */
    private BigDecimal interestPenaltyRate;

    /** Phí tất toán trước hạn (% gốc còn lại). */
    private BigDecimal earlySettlementFeeRate;

    /** Ngưỡng miễn phí tất toán (tỷ lệ kỳ hạn đã dùng, ví dụ 0.6667 = 2/3). */
    private BigDecimal earlySettlementFreeRatio;

    /** Mức phí tất toán tối thiểu (VND). */
    private BigDecimal earlySettlementMinFee;

    /** Thứ tự hiển thị. */
    private Integer sortOrder;

    /** Bật/tắt sản phẩm. */
    private Boolean active;
}
