package com.p2plending.loan.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
public class DueTodayItem {
    private String scheduleId;
    private String loanId;
    private String loanCode;
    private String borrowerId;
    private int periodNumber;
    private LocalDate dueDate;
    private BigDecimal principalDue;
    private BigDecimal interestDue;
    private BigDecimal totalDue;
    private BigDecimal lateFee;
    private BigDecimal paidAmount;
    private BigDecimal lateFeePaid;
    private BigDecimal principalPaid;
    private BigDecimal interestPaid;
    private BigDecimal principalOutstanding;
    private BigDecimal interestOutstanding;
    private BigDecimal lateFeeOutstanding;
    private BigDecimal remaining;
    /** Tổng dư nợ còn lại của TẤT CẢ kỳ chưa thanh toán (kỳ này + mọi kỳ trước còn nợ). */
    private BigDecimal totalDebt;
    private String status;
    private int dpd;
}
