package com.p2plending.loan.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Dữ liệu dòng tiền của nhà đầu tư — trả về cho màn "Dòng tiền" trên mobile app. */
@Data
@Builder
public class CashflowResponse {

    private Summary       summary;
    private List<UpcomingPayment> upcomingPayments;
    private List<InvestmentItem>  investmentHistory;
    private List<MonthlyChartItem> monthlyChart;

    @Data
    @Builder
    public static class Summary {
        /** Tổng tiền đã đầu tư vào các khoản đang hoạt động / đang trả / đã hoàn thành. */
        private BigDecimal totalInvested;
        /** Tổng lãi + gốc nhà đầu tư dự kiến nhận về (theo lịch). */
        private BigDecimal totalReturnsExpected;
        /** Tổng số tiền đã thực nhận (từ các kỳ PAID). */
        private BigDecimal totalReturnsPaid;
        /** Ngày đến hạn kỳ thanh toán gần nhất — null nếu chưa có. */
        private LocalDate  nextPaymentDate;
        /** Phần nhà đầu tư nhận kỳ gần nhất (tỷ lệ theo offer). */
        private BigDecimal nextPaymentAmount;
    }

    @Data
    @Builder
    public static class UpcomingPayment {
        private String     loanId;
        private String     loanCode;
        private LocalDate  dueDate;
        private int        periodNumber;
        /** Phần của nhà đầu tư trong kỳ này (offer.amount / fundedAmount * totalDue). */
        private BigDecimal investorShare;
        private String     status;    // PENDING | PARTIAL | OVERDUE
        private int        dpd;
    }

    @Data
    @Builder
    public static class InvestmentItem {
        private String        offerId;
        private String        loanId;
        private String        loanCode;
        /** Số tiền nhà đầu tư đã đặt. */
        private BigDecimal    amount;
        private String        loanStatus;
        /** Lãi suất khoản vay (%/năm) — null nếu chưa phê duyệt. */
        private BigDecimal    interestRate;
        private Integer       termMonths;
        private LocalDateTime investedAt;
    }

    @Data
    @Builder
    public static class MonthlyChartItem {
        /** "yyyy-MM", ví dụ "2025-06". */
        private String     month;
        /** Tổng dự kiến nhà đầu tư nhận về trong tháng (theo lịch). */
        private BigDecimal expected;
        /** Tổng đã thực nhận trong tháng. */
        private BigDecimal actual;
    }
}
