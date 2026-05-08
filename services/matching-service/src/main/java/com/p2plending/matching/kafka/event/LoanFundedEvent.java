package com.p2plending.matching.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data @NoArgsConstructor @AllArgsConstructor
public class LoanFundedEvent {
    private Long loanId;
    private Long borrowerId;
    private BigDecimal totalAmount;
    private LocalDateTime fundedAt;
}
