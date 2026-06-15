package com.p2plending.loan.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Phát khi người gọi vốn trả một kỳ (toàn bộ hoặc một phần) và tiền đã được phân bổ về ví nhà đầu tư.
 * notification-service consume để thông báo cho người gọi vốn (đã trả / trả một phần / tất toán)
 * và cho từng nhà đầu tư (số tiền hoàn trả nhận được).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanRepaidEvent {
    private String loanId;
    private String loanCode;
    private String borrowerId;

    private Integer periodNumber;
    private Integer totalPeriods;

    /** Số tiền trả trong giao dịch này (có thể nhỏ hơn periodTotalDue nếu trả một phần). */
    private BigDecimal amountPaid;
    /** Tổng phải trả của kỳ này (gốc + lãi). */
    private BigDecimal periodTotalDue;

    /** true = lần trả này mới chỉ thanh toán một phần kỳ (số dư ví không đủ). */
    private boolean partial;
    /** true = sau giao dịch này khoản đã tất toán toàn bộ (COMPLETED). */
    private boolean loanCompleted;

    /** Kênh: WALLET (chủ động trên app) hoặc AUTO_DEBIT (tự động đến hạn). */
    private String channel;

    private LocalDateTime paidAt;

    /** Phân bổ pro-rata cho từng nhà đầu tư đã được cộng ví thành công. */
    private List<InvestorPayout> investorPayouts;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InvestorPayout {
        private String investorId;
        private BigDecimal amount;
    }
}
