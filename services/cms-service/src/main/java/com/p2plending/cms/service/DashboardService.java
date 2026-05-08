package com.p2plending.cms.service;

import com.p2plending.cms.config.CacheConfig;
import com.p2plending.cms.domain.entity.DailyStat;
import com.p2plending.cms.domain.enums.UserAccountStatus;
import com.p2plending.cms.domain.repository.CmsLoanRepository;
import com.p2plending.cms.domain.repository.CmsUserRepository;
import com.p2plending.cms.domain.repository.DailyStatRepository;
import com.p2plending.cms.dto.response.ChartDataResponse;
import com.p2plending.cms.dto.response.DashboardStatsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final CmsUserRepository  userRepo;
    private final CmsLoanRepository  loanRepo;
    private final DailyStatRepository statRepo;

    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.CACHE_DASHBOARD_STATS, key = "'global'")
    public DashboardStatsResponse getStats() {
        DailyStat today = statRepo.findByStatDate(LocalDate.now())
                .orElse(DailyStat.builder().statDate(LocalDate.now()).build());

        return DashboardStatsResponse.builder()
                .totalUsers(userRepo.count())
                .activeUsers(userRepo.countByAccountStatus(UserAccountStatus.ACTIVE))
                .pendingKycCount(userRepo.countByKycStatus("PENDING"))
                .totalLoans(loanRepo.count())
                .pendingLoans(loanRepo.countByStatus("PENDING"))
                .activeLoans(loanRepo.countByStatus("ACTIVE"))
                .fundedLoans(loanRepo.countByStatus("FUNDED"))
                .totalFundedVolume(loanRepo.sumFundedVolume())
                .todayNewUsers(today.getNewUsers())
                .todayNewLoans(today.getNewLoans())
                .todayLoanVolume(today.getLoanVolume())
                .build();
    }

    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.CACHE_DASHBOARD_CHART, key = "'30d'")
    public ChartDataResponse getChartData() {
        List<ChartDataResponse.DataPoint> points = statRepo.findTop30ByOrderByStatDateDesc()
                .stream()
                .map(s -> ChartDataResponse.DataPoint.builder()
                        .date(s.getStatDate())
                        .newUsers(s.getNewUsers())
                        .newLoans(s.getNewLoans())
                        .fundedLoans(s.getFundedLoans())
                        .loanVolume(s.getLoanVolume())
                        .build())
                .sorted(java.util.Comparator.comparing(ChartDataResponse.DataPoint::getDate))
                .toList();
        return ChartDataResponse.builder().points(points).build();
    }
}
