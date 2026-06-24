package com.p2plending.loan.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepaymentMonitoringResponse {
    private LocalDate asOfDate;
    private int dueWithinDays;
    private long dueSoonInstallments;
    private long dueSoonCustomers;
    private long overdueInstallments;
    private long overdueCustomers;
    private BigDecimal outstandingPrincipal;
    private BigDecimal outstandingInterest;
    private BigDecimal outstandingLateFee;
    private BigDecimal totalOutstanding;
    private List<RepaymentAttentionItem> attentionItems;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RepaymentAttentionItem {
        private String loanId;
        private String loanCode;
        private String borrowerId;
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
