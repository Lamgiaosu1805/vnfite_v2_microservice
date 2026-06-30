package com.p2plending.cms.dto.response;

import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DashboardStatsResponse {
    private long totalUsers;
    private long activeUsers;
    private long pendingKycCount;
    private long totalLoans;
    private long pendingLoans;
    private long activeLoans;
    private long fundedLoans;
    private BigDecimal activeFundingVolume;
    private BigDecimal totalFundedVolume;
    /** Doanh thu phí đã thu (khoản đã giải ngân): phí thẩm định, VAT, tổng phí. */
    private BigDecimal totalAppraisalFee;
    private BigDecimal totalVatCollected;
    private BigDecimal totalFeeRevenue;
    private long todayNewUsers;
    private long todayNewLoans;
    private BigDecimal todayLoanVolume;
    private LocalDate debtAsOfDate;
    private int dueWithinDays;
    private long dueSoonInstallments;
    private long dueSoonCustomers;
    private long overdueInstallments;
    private long overdueCustomers;
    private BigDecimal outstandingPrincipal;
    private BigDecimal outstandingInterest;
    private BigDecimal outstandingLateFee;
    private BigDecimal totalOutstanding;
    private List<RepaymentAttentionItem> repaymentAttentionItems;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class RepaymentAttentionItem {
        private String loanId;
        private String loanCode;
        private String borrowerId;
        private String borrowerName;
        private String borrowerPhone;
        private Integer periodNumber;
        private LocalDate dueDate;
        private int dpd;
        private String status;
        private BigDecimal principalOutstanding;
        private BigDecimal interestOutstanding;
        private BigDecimal lateFeeOutstanding;
        private BigDecimal totalOutstanding;
    }
}
