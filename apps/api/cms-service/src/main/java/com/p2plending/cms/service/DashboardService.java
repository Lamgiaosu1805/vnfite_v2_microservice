package com.p2plending.cms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.p2plending.cms.config.CacheConfig;
import com.p2plending.cms.dto.response.ChartDataResponse;
import com.p2plending.cms.dto.response.DashboardStatsResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    private static final ZoneId TZ = ZoneId.of("Asia/Ho_Chi_Minh");

    private final SourceServiceClient sourceServiceClient;

    @Cacheable(value = CacheConfig.CACHE_DASHBOARD_STATS)
    public DashboardStatsResponse getStats() {
        try {
            LocalDate from = LocalDate.now(TZ).minusDays(30);
            JsonNode u = sourceServiceClient.getUserStats(from);
            JsonNode l = sourceServiceClient.getLoanStats(from);

            return DashboardStatsResponse.builder()
                    .totalUsers(u.path("totalUsers").asLong())
                    .activeUsers(u.path("totalUsers").asLong())
                    .pendingKycCount(u.path("pendingKyc").asLong())
                    .todayNewUsers(u.path("newUsersToday").asLong())
                    .totalLoans(l.path("totalLoans").asLong())
                    .pendingLoans(l.path("pendingLoans").asLong())
                    .activeLoans(l.path("activeLoans").asLong())
                    .fundedLoans(l.path("fundedLoans").asLong())
                    .totalFundedVolume(decimal(l, "totalFundedVolume"))
                    .todayNewLoans(l.path("newLoansToday").asLong())
                    .todayLoanVolume(decimal(l, "todayLoanVolume"))
                    .build();
        } catch (Exception ex) {
            log.error("Failed to fetch dashboard stats", ex);
            return emptyStats();
        }
    }

    @Cacheable(value = CacheConfig.CACHE_DASHBOARD_CHART, key = "#period")
    public ChartDataResponse getChartData(String period) {
        try {
            LocalDate today = LocalDate.now(TZ);
            LocalDate from = switch (period) {
                case "week"  -> today.minusWeeks(11).with(WeekFields.ISO.dayOfWeek(), 1);
                case "month" -> today.minusMonths(11).withDayOfMonth(1);
                default      -> today.minusDays(29); // day
            };

            JsonNode u = sourceServiceClient.getUserStats(from);
            JsonNode l = sourceServiceClient.getLoanStats(from);

            // Build map from date → [userCount, loanCount, loanVolume]
            Map<String, long[]> userMap = new LinkedHashMap<>();
            u.path("dailyCounts").forEach(node -> {
                String date = node.path("date").asText();
                userMap.put(date, new long[]{ node.path("count").asLong() });
            });

            Map<String, Object[]> loanMap = new LinkedHashMap<>();
            l.path("dailyCounts").forEach(node -> {
                String date = node.path("date").asText();
                loanMap.put(date, new Object[]{
                    node.path("count").asLong(),
                    decimal(node, "volume")
                });
            });

            return switch (period) {
                case "week"  -> buildWeeklyChart(from, today, userMap, loanMap);
                case "month" -> buildMonthlyChart(from, today, userMap, loanMap);
                default      -> buildDailyChart(from, today, userMap, loanMap);
            };
        } catch (Exception ex) {
            log.error("Failed to fetch chart data for period={}", period, ex);
            return ChartDataResponse.builder().points(Collections.emptyList()).build();
        }
    }

    // ─── Chart builders ───────────────────────────────────────────────────────

    private ChartDataResponse buildDailyChart(LocalDate from, LocalDate today,
            Map<String, long[]> userMap, Map<String, Object[]> loanMap) {
        List<ChartDataResponse.DataPoint> points = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(today); d = d.plusDays(1)) {
            String key = d.toString();
            long newUsers = userMap.containsKey(key) ? userMap.get(key)[0] : 0;
            long newLoans = loanMap.containsKey(key) ? (long) loanMap.get(key)[0] : 0;
            BigDecimal vol = loanMap.containsKey(key) ? (BigDecimal) loanMap.get(key)[1] : BigDecimal.ZERO;
            points.add(ChartDataResponse.DataPoint.builder()
                    .date(d).label(d.format(DateTimeFormatter.ofPattern("dd/MM")))
                    .newUsers(newUsers).newLoans(newLoans).loanVolume(vol).build());
        }
        return ChartDataResponse.builder().points(points).build();
    }

    private ChartDataResponse buildWeeklyChart(LocalDate from, LocalDate today,
            Map<String, long[]> userMap, Map<String, Object[]> loanMap) {
        List<ChartDataResponse.DataPoint> points = new ArrayList<>();
        LocalDate weekStart = from;
        while (!weekStart.isAfter(today)) {
            LocalDate weekEnd = weekStart.plusDays(6);
            long newUsers = 0, newLoans = 0;
            BigDecimal vol = BigDecimal.ZERO;
            for (LocalDate d = weekStart; !d.isAfter(weekEnd) && !d.isAfter(today); d = d.plusDays(1)) {
                String key = d.toString();
                if (userMap.containsKey(key)) newUsers += userMap.get(key)[0];
                if (loanMap.containsKey(key)) {
                    newLoans += (long) loanMap.get(key)[0];
                    vol = vol.add((BigDecimal) loanMap.get(key)[1]);
                }
            }
            String label = weekStart.format(DateTimeFormatter.ofPattern("dd/MM"))
                    + "-" + weekEnd.format(DateTimeFormatter.ofPattern("dd/MM"));
            points.add(ChartDataResponse.DataPoint.builder()
                    .date(weekStart).label(label)
                    .newUsers(newUsers).newLoans(newLoans).loanVolume(vol).build());
            weekStart = weekStart.plusWeeks(1);
        }
        return ChartDataResponse.builder().points(points).build();
    }

    private ChartDataResponse buildMonthlyChart(LocalDate from, LocalDate today,
            Map<String, long[]> userMap, Map<String, Object[]> loanMap) {
        List<ChartDataResponse.DataPoint> points = new ArrayList<>();
        LocalDate monthStart = from;
        while (!monthStart.isAfter(today)) {
            LocalDate monthEnd = monthStart.plusMonths(1).minusDays(1);
            long newUsers = 0, newLoans = 0;
            BigDecimal vol = BigDecimal.ZERO;
            for (LocalDate d = monthStart; !d.isAfter(monthEnd) && !d.isAfter(today); d = d.plusDays(1)) {
                String key = d.toString();
                if (userMap.containsKey(key)) newUsers += userMap.get(key)[0];
                if (loanMap.containsKey(key)) {
                    newLoans += (long) loanMap.get(key)[0];
                    vol = vol.add((BigDecimal) loanMap.get(key)[1]);
                }
            }
            String label = monthStart.format(DateTimeFormatter.ofPattern("MM/yyyy"));
            points.add(ChartDataResponse.DataPoint.builder()
                    .date(monthStart).label(label)
                    .newUsers(newUsers).newLoans(newLoans).loanVolume(vol).build());
            monthStart = monthStart.plusMonths(1);
        }
        return ChartDataResponse.builder().points(points).build();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private BigDecimal decimal(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).decimalValue() : BigDecimal.ZERO;
    }

    private DashboardStatsResponse emptyStats() {
        return DashboardStatsResponse.builder()
                .totalUsers(0).activeUsers(0).pendingKycCount(0)
                .totalLoans(0).pendingLoans(0).activeLoans(0).fundedLoans(0)
                .totalFundedVolume(BigDecimal.ZERO)
                .todayNewUsers(0).todayNewLoans(0).todayLoanVolume(BigDecimal.ZERO)
                .build();
    }
}
