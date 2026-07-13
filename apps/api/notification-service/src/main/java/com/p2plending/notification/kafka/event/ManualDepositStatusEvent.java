package com.p2plending.notification.kafka.event;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ManualDepositStatusEvent {
    private String requestId;
    private String userId;
    private BigDecimal amount;
    private String status;
    private String rejectionReason;
}
