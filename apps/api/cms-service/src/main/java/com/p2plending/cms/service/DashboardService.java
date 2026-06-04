package com.p2plending.cms.service;

import com.p2plending.cms.dto.response.ChartDataResponse;
import com.p2plending.cms.dto.response.DashboardStatsResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collections;

@Service
public class DashboardService {

    public DashboardStatsResponse getStats() {
        return DashboardStatsResponse.builder()
                .totalUsers(0)
                .activeUsers(0)
                .pendingKycCount(0)
                .totalLoans(0)
                .pendingLoans(0)
                .activeLoans(0)
                .fundedLoans(0)
                .totalFundedVolume(BigDecimal.ZERO)
                .todayNewUsers(0)
                .todayNewLoans(0)
                .todayLoanVolume(BigDecimal.ZERO)
                .build();
    }

    public ChartDataResponse getChartData() {
        return ChartDataResponse.builder().points(Collections.emptyList()).build();
    }
}
