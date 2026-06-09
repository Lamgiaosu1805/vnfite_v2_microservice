package com.p2plending.notification.kafka.event;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class LoanDisbursedEvent {
    private String loanId;
    private String loanCode;
    private String borrowerId;
    private BigDecimal amount;
    private LocalDateTime disbursedAt;
    private List<String> investorIds;
}
