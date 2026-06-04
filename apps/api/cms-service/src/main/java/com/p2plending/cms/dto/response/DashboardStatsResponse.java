package com.p2plending.cms.dto.response;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data @Builder @NoArgsConstructor
public class DashboardStatsResponse {
    private long totalUsers;
    private long activeUsers;
    private long pendingKycCount;
    private long totalLoans;
    private long pendingLoans;
    private long activeLoans;
    private long fundedLoans;
    private BigDecimal totalFundedVolume;
    private long todayNewUsers;
    private long todayNewLoans;
    private BigDecimal todayLoanVolume;
}
