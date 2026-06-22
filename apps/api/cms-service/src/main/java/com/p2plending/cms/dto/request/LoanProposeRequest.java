package com.p2plending.cms.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

/** Thẩm định viên đề xuất số tiền + lãi suất trình ban lãnh đạo. */
@Data
public class LoanProposeRequest {

    @NotNull(message = "Số tiền đề xuất bắt buộc")
    @DecimalMin(value = "0.01", message = "Số tiền đề xuất phải lớn hơn 0")
    private BigDecimal proposedAmount;

    @NotNull(message = "Lãi suất đề xuất bắt buộc")
    @DecimalMin(value = "0.01", message = "Lãi suất phải lớn hơn 0")
    @DecimalMax(value = "100.00", message = "Lãi suất không vượt 100%")
    @Digits(integer = 3, fraction = 2)
    private BigDecimal proposedInterestRate;

    /** % phí thẩm định thẩm định viên đề xuất (vd: 2.00 = 2%). Null hoặc 0 = không tính phí. */
    @DecimalMin(value = "0.00", message = "Phí thẩm định không được âm")
    @DecimalMax(value = "100.00", message = "Phí thẩm định không vượt 100%")
    @Digits(integer = 3, fraction = 2)
    private BigDecimal appraisalFeeRate;

    @Size(max = 1000)
    private String note;
}
