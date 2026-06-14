package com.p2plending.notification.kafka.event;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** Tiền đầu tư được hoàn cho nhà đầu tư (khoản hết hạn gọi vốn / không ký khế ước). */
@Data
public class InvestmentRefundedEvent {
    private String loanId;
    private String loanCode;
    private String reason;
    private List<Refund> refunds;

    @Data
    public static class Refund {
        private String investorId;
        private BigDecimal amount;
    }
}
