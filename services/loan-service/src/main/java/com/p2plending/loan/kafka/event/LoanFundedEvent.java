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
public class LoanFundedEvent {
    private String loanId;
    private String borrowerId;
    private BigDecimal totalAmount;
    private LocalDateTime fundedAt;
}
