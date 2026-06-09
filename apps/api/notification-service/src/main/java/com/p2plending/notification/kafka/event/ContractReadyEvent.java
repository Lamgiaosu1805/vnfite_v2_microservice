package com.p2plending.notification.kafka.event;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ContractReadyEvent {
    private String loanId;
    private String loanCode;
    private String borrowerId;
    private String contractId;
    private BigDecimal amount;
    private BigDecimal interestRate;
    private Integer termMonths;
    private LocalDateTime issuedAt;
}
