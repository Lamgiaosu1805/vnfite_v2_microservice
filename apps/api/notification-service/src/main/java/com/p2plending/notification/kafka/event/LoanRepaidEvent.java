package com.p2plending.notification.kafka.event;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Người gọi vốn trả một kỳ; tiền đã phân bổ về ví nhà đầu tư. Thông báo cho borrower + investors. */
@Data
public class LoanRepaidEvent {
    private String loanId;
    private String loanCode;
    private String borrowerId;

    private Integer periodNumber;
    private Integer totalPeriods;

    private BigDecimal amountPaid;
    private BigDecimal periodTotalDue;

    private boolean partial;
    private boolean loanCompleted;

    private String channel;
    private LocalDateTime paidAt;

    private List<InvestorPayout> investorPayouts;

    @Data
    public static class InvestorPayout {
        private String investorId;
        private BigDecimal amount;
    }
}
