package com.p2plending.loan.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class InternalLoanStatsResponse {
    private long totalLoans;
    private long pendingLoans;
    private long activeLoans;
    private long fundedLoans;
    private BigDecimal activeFundingVolume;
    private BigDecimal totalFundedVolume;
    private long newLoansToday;
    private BigDecimal todayLoanVolume;
    private List<DailyCount> dailyCounts;

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class DailyCount {
        private LocalDate date;
        private long count;
        private BigDecimal volume;
    }
}
