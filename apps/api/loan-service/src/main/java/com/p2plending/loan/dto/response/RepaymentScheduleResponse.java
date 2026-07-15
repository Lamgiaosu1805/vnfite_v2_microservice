package com.p2plending.loan.dto.response;

import com.p2plending.loan.domain.enums.RepaymentStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class RepaymentScheduleResponse {
    private Integer periodNumber;
    private LocalDate dueDate;
    private BigDecimal principalDue;
    private BigDecimal interestDue;
    private BigDecimal totalDue;
    private BigDecimal paidAmount;
    private BigDecimal principalPaid;
    private BigDecimal interestPaid;
    /** Phí phạt trả chậm đã tính của kỳ. */
    private BigDecimal lateFee;
    /** Phí phạt còn phải trả (lateFee - đã trả). */
    private BigDecimal lateFeeOutstanding;
    /** Tổng còn phải trả của kỳ = gốc + lãi + phí phạt còn lại. */
    private BigDecimal totalOutstanding;

    /** Phí phạt lãi quá hạn đã tích lũy (10%/năm × lãi chưa trả × DPD). */
    private BigDecimal interestPenalty;
    /** Phí phạt gốc quá hạn đã tích lũy (150%×rate/năm × gốc chưa trả × DPD). */
    private BigDecimal principalPenalty;
    private BigDecimal interestPenaltyPaid;
    private BigDecimal principalPenaltyPaid;
    private BigDecimal interestPenaltyOutstanding;
    private BigDecimal principalPenaltyOutstanding;
    /** Gốc còn lại chưa trả của kỳ. */
    private BigDecimal principalOutstanding;
    /** Lãi còn lại chưa trả của kỳ. */
    private BigDecimal interestOutstanding;

    private RepaymentStatus status;
    private Integer dpd;
    private LocalDateTime paidAt;
}
