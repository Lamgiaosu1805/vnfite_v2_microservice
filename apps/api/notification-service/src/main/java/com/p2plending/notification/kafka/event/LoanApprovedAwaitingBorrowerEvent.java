package com.p2plending.notification.kafka.event;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
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
