package com.p2plending.loan.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanApprovedAwaitingBorrowerEvent {
    private String loanId;
    private String loanCode;
    private String borrowerId;
    private BigDecimal approvedAmount;
    private BigDecimal approvedInterestRate;
    private Integer termMonths;
    private String reviewedBy;
    private LocalDateTime approvedAt;
}
