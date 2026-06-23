package com.p2plending.loan.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/** Đề xuất của thẩm định viên trình ban lãnh đạo: số tiền + lãi suất + ghi chú. */
@Data
public class InternalLoanProposeRequest {

    @NotNull(message = "Số tiền đề xuất bắt buộc")
    @DecimalMin(value = "0.01", message = "Số tiền đề xuất phải lớn hơn 0")
    private BigDecimal proposedAmount;

    @NotNull(message = "Lãi suất đề xuất bắt buộc")
    @DecimalMin(value = "0.01", message = "Lãi suất phải lớn hơn 0")
    @DecimalMax(value = "100.00", message = "Lãi suất không vượt 100%")
    @Digits(integer = 3, fraction = 2)
    private BigDecimal proposedInterestRate;

    /** % phí thẩm định (vd: 2.00 = 2%). Dùng 0.00 nếu chủ động miễn phí. */
    @NotNull(message = "Tỷ lệ phí thẩm định bắt buộc (nhập 0.00 nếu miễn phí)")
    @DecimalMin(value = "0.00", message = "Phí thẩm định không được âm")
    @DecimalMax(value = "100.00", message = "Phí thẩm định không vượt 100%")
    @Digits(integer = 3, fraction = 2)
    private BigDecimal appraisalFeeRate;

    @Size(max = 1000)
    private String note;

    private String proposedBy;
}
