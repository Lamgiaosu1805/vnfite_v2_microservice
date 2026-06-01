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
public class LoanSubmittedEvent {
    private String loanId;
    private String borrowerId;
    private BigDecimal amount;
    private Integer termMonths;
    private String purpose;
    private String referredBy;
    private BigDecimal monthlyIncome;
    private String occupation;
    private String currentAddress;
    private LocalDateTime submittedAt;
}
