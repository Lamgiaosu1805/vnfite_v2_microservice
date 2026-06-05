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
    private RepaymentStatus status;
    private Integer dpd;
    private LocalDateTime paidAt;
}
