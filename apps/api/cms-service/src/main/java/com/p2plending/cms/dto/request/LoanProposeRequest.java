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

    @Size(max = 1000)
    private String note;
}
