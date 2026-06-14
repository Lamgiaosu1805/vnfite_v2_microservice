package com.p2plending.loan.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Phát khi tiền đầu tư được hoàn cho nhà đầu tư (khoản hết hạn gọi vốn hoặc người gọi vốn
 * không ký khế ước). notification-service consume để thông báo cho từng nhà đầu tư số tiền hoàn.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvestmentRefundedEvent {
    private String loanId;
    private String loanCode;
    private String reason;
    private List<Refund> refunds;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Refund {
        private String investorId;
        private BigDecimal amount;
    }
}
