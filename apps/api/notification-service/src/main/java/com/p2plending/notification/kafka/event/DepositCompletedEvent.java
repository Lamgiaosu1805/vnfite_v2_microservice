package com.p2plending.notification.kafka.event;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class DepositCompletedEvent {
    private String userId;
    private BigDecimal amount;
    private BigDecimal balance;
    private String txnId;
}
