package com.p2plending.matching.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data @NoArgsConstructor @AllArgsConstructor
public class LoanCreatedEvent {
    private String loanId;
    private String borrowerId;
    private BigDecimal amount;
    private BigDecimal interestRate;
    private Integer termMonths;
    private String purpose;
    private LocalDateTime createdAt;
}
