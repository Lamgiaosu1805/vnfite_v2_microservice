package com.p2plending.cms.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvestorCashflowResponse {
    private Summary summary;
    private List<UpcomingPayment> upcomingPayments;
    private List<InvestmentItem> investmentHistory;
    private PagedResponse<InvestmentItem> investmentHistoryPage;
    private List<MonthlyChartItem> monthlyChart;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Summary {
        private BigDecimal totalInvested;
        private BigDecimal totalReturnsExpected;
        private BigDecimal totalReturnsPaid;
        private LocalDate nextPaymentDate;
        private BigDecimal nextPaymentAmount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpcomingPayment {
        private String loanId;
        private String loanCode;
        private LocalDate dueDate;
        private int periodNumber;
        private BigDecimal investorShare;
        private String status;
        private int dpd;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InvestmentItem {
        private String offerId;
        private String loanId;
        private String loanCode;
        private String borrowerId;
        private String borrowerName;
        private String borrowerPhone;
        private BigDecimal amount;
        private String loanStatus;
        private BigDecimal interestRate;
        private Integer termMonths;
        private LocalDateTime investedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthlyChartItem {
        private String month;
        private BigDecimal expected;
        private BigDecimal actual;
    }
}
