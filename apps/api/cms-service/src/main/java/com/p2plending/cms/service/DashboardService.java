package com.p2plending.cms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.p2plending.cms.config.CacheConfig;
import com.p2plending.cms.dto.response.ChartDataResponse;
import com.p2plending.cms.dto.response.DashboardStatsResponse;
import com.p2plending.cms.dto.response.UserSummaryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    private static final ZoneId TZ = ZoneId.of("Asia/Ho_Chi_Minh");
    /** T2, T3, T4, T5, T6, T7, CN */
    private static final String[] DOW_VN = { "T2", "T3", "T4", "T5", "T6", "T7", "CN" };

    private final SourceServiceClient sourceServiceClient;

    @Cacheable(value = CacheConfig.CACHE_DASHBOARD_STATS)
    public DashboardStatsResponse getStats() {
        LocalDate from = LocalDate.now(TZ).minusDays(1);
        JsonNode u = sourceServiceClient.getUserStats(from);
        JsonNode l = sourceServiceClient.getLoanStats(from);
        JsonNode debt = sourceServiceClient.getRepaymentMonitoring(7);

        return DashboardStatsResponse.builder()
                .totalUsers(u.path("totalUsers").asLong())
                .activeUsers(u.path("totalUsers").asLong())
                .pendingKycCount(u.path("pendingKyc").asLong())
                .todayNewUsers(u.path("newUsersToday").asLong())
                .totalLoans(l.path("totalLoans").asLong())
                .pendingLoans(l.path("pendingLoans").asLong())
                .activeLoans(l.path("activeLoans").asLong())
                .fundedLoans(l.path("fundedLoans").asLong())
                .activeFundingVolume(decimal(l, "activeFundingVolume"))
                .totalFundedVolume(decimal(l, "totalFundedVolume"))
                .todayNewLoans(l.path("newLoansToday").asLong())
                .todayLoanVolume(decimal(l, "todayLoanVolume"))
                .debtAsOfDate(localDate(debt, "asOfDate"))
                .dueWithinDays(debt.path("dueWithinDays").asInt(7))
                .dueSoonInstallments(debt.path("dueSoonInstallments").asLong())
                .dueSoonCustomers(debt.path("dueSoonCustomers").asLong())
                .overdueInstallments(debt.path("overdueInstallments").asLong())
                .overdueCustomers(debt.path("overdueCustomers").asLong())
                .outstandingPrincipal(decimal(debt, "outstandingPrincipal"))
                .outstandingInterest(decimal(debt, "outstandingInterest"))
                .outstandingLateFee(decimal(debt, "outstandingLateFee"))
                .totalOutstanding(decimal(debt, "totalOutstanding"))
                .repaymentAttentionItems(buildRepaymentItems(debt.path("attentionItems")))
                .build();
    }

    /**
     * period:
     *   "week"  → T2–CN của tuần hiện tại (7 cột)
     *   "month" → từng ngày trong tháng hiện tại (28–31 cột)
     *   "year"  → 12 tháng của năm hiện tại (12 cột)
     */
    @Cacheable(value = CacheConfig.CACHE_DASHBOARD_CHART, key = "#period")
    public ChartDataResponse getChartData(String period) {
        LocalDate today = LocalDate.now(TZ);

        LocalDate from = switch (period) {
            case "month" -> today.with(TemporalAdjusters.firstDayOfMonth());
            case "year"  -> today.with(TemporalAdjusters.firstDayOfYear());
            default      -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); // week
        };

        JsonNode u = sourceServiceClient.getUserStats(from);
        JsonNode l = sourceServiceClient.getLoanStats(from);

        Map<String, long[]>   userMap = buildUserMap(u);
        Map<String, Object[]> loanMap = buildLoanMap(l);

        return switch (period) {
            case "month" -> buildMonthlyByDay(today, userMap, loanMap);
            case "year"  -> buildYearlyByMonth(today, userMap, loanMap);
            default      -> buildWeekByDay(from, today, userMap, loanMap);
        };
    }

    // ─── Chart builders ───────────────────────────────────────────────────────

    /** Tuần: T2 → CN của tuần hiện tại (7 cột cố định) */
    private ChartDataResponse buildWeekByDay(LocalDate monday, LocalDate today,
            Map<String, long[]> userMap, Map<String, Object[]> loanMap) {
        List<ChartDataResponse.DataPoint> points = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate d = monday.plusDays(i);
            String key = d.toString();
            long newUsers = userMap.containsKey(key) ? userMap.get(key)[0] : 0;
            long newLoans = loanMap.containsKey(key) ? (long) loanMap.get(key)[0] : 0;
            BigDecimal vol = loanMap.containsKey(key) ? (BigDecimal) loanMap.get(key)[1] : BigDecimal.ZERO;
            // Ngày tương lai → null (chưa có data)
            boolean future = d.isAfter(today);
            points.add(ChartDataResponse.DataPoint.builder()
                    .date(d)
                    .label(DOW_VN[i])
                    .newUsers(future ? 0 : newUsers)
                    .newLoans(future ? 0 : newLoans)
                    .loanVolume(future ? BigDecimal.ZERO : vol)
                    .future(future)
                    .build());
        }
        return ChartDataResponse.builder().points(points).build();
    }

    /** Tháng: từng ngày 1 → cuối tháng hiện tại */
    private ChartDataResponse buildMonthlyByDay(LocalDate today,
            Map<String, long[]> userMap, Map<String, Object[]> loanMap) {
        LocalDate first = today.with(TemporalAdjusters.firstDayOfMonth());
        LocalDate last  = today.with(TemporalAdjusters.lastDayOfMonth());
        List<ChartDataResponse.DataPoint> points = new ArrayList<>();
        for (LocalDate d = first; !d.isAfter(last); d = d.plusDays(1)) {
            String key = d.toString();
            boolean future = d.isAfter(today);
            long newUsers = (!future && userMap.containsKey(key)) ? userMap.get(key)[0] : 0;
            long newLoans = (!future && loanMap.containsKey(key)) ? (long) loanMap.get(key)[0] : 0;
            BigDecimal vol = (!future && loanMap.containsKey(key)) ? (BigDecimal) loanMap.get(key)[1] : BigDecimal.ZERO;
            points.add(ChartDataResponse.DataPoint.builder()
                    .date(d)
                    .label(String.valueOf(d.getDayOfMonth()))
                    .newUsers(newUsers).newLoans(newLoans).loanVolume(vol)
                    .future(future)
                    .build());
        }
        return ChartDataResponse.builder().points(points).build();
    }

    /** Năm: T1–T12 — gộp daily counts theo tháng */
    private ChartDataResponse buildYearlyByMonth(LocalDate today,
            Map<String, long[]> userMap, Map<String, Object[]> loanMap) {
        int year = today.getYear();
        List<ChartDataResponse.DataPoint> points = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            LocalDate monthStart = LocalDate.of(year, m, 1);
            LocalDate monthEnd   = monthStart.with(TemporalAdjusters.lastDayOfMonth());
            boolean future = monthStart.isAfter(today);
            long newUsers = 0, newLoans = 0;
            BigDecimal vol = BigDecimal.ZERO;
            if (!future) {
                for (LocalDate d = monthStart; !d.isAfter(monthEnd) && !d.isAfter(today); d = d.plusDays(1)) {
                    String key = d.toString();
                    if (userMap.containsKey(key)) newUsers += userMap.get(key)[0];
                    if (loanMap.containsKey(key)) {
                        newLoans += (long) loanMap.get(key)[0];
                        vol = vol.add((BigDecimal) loanMap.get(key)[1]);
                    }
                }
            }
            points.add(ChartDataResponse.DataPoint.builder()
                    .date(monthStart)
                    .label("T" + m)
                    .newUsers(newUsers).newLoans(newLoans).loanVolume(vol)
                    .future(future)
                    .build());
        }
        return ChartDataResponse.builder().points(points).build();
    }

    // ─── Map builders ─────────────────────────────────────────────────────────

    private Map<String, long[]> buildUserMap(JsonNode u) {
        Map<String, long[]> map = new LinkedHashMap<>();
        u.path("dailyCounts").forEach(n -> map.put(n.path("date").asText(), new long[]{ n.path("count").asLong() }));
        return map;
    }

    private Map<String, Object[]> buildLoanMap(JsonNode l) {
        Map<String, Object[]> map = new LinkedHashMap<>();
        l.path("dailyCounts").forEach(n -> map.put(n.path("date").asText(),
                new Object[]{ n.path("count").asLong(), decimal(n, "volume") }));
        return map;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private BigDecimal decimal(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).decimalValue() : BigDecimal.ZERO;
    }

    private LocalDate localDate(JsonNode node, String field) {
        return node.hasNonNull(field) ? LocalDate.parse(node.get(field).asText()) : null;
    }

    private List<DashboardStatsResponse.RepaymentAttentionItem> buildRepaymentItems(JsonNode nodes) {
        List<DashboardStatsResponse.RepaymentAttentionItem> items = new ArrayList<>();
        Map<String, UserSummaryResponse> users = new HashMap<>();
        for (JsonNode node : nodes) {
            String borrowerId = node.path("borrowerId").asText(null);
            UserSummaryResponse borrower = null;
            if (borrowerId != null) {
                borrower = users.get(borrowerId);
                if (!users.containsKey(borrowerId)) {
                    try {
                        borrower = sourceServiceClient.getUser(borrowerId);
                    } catch (Exception ex) {
                        log.warn("Không lấy được khách hàng {} cho Dashboard công nợ: {}",
                                borrowerId, ex.getMessage());
                    }
                    users.put(borrowerId, borrower);
                }
            }
            items.add(DashboardStatsResponse.RepaymentAttentionItem.builder()
                    .loanId(node.path("loanId").asText())
                    .loanCode(node.path("loanCode").asText(null))
                    .borrowerId(borrowerId)
                    .borrowerName(resolveBorrowerName(borrower, borrowerId))
                    .borrowerPhone(borrower != null ? borrower.getPhone() : null)
                    .periodNumber(node.path("periodNumber").isNumber() ? node.path("periodNumber").asInt() : null)
                    .dueDate(localDate(node, "dueDate"))
                    .dpd(node.path("dpd").asInt())
                    .status(node.path("status").asText())
                    .principalOutstanding(decimal(node, "principalOutstanding"))
                    .interestOutstanding(decimal(node, "interestOutstanding"))
                    .lateFeeOutstanding(decimal(node, "lateFeeOutstanding"))
                    .totalOutstanding(decimal(node, "totalOutstanding"))
                    .build());
        }
        return items;
    }

    private String resolveBorrowerName(UserSummaryResponse borrower, String borrowerId) {
        if (borrower == null) return borrowerId;
        if (borrower.getFullName() != null && !borrower.getFullName().isBlank()) {
            return borrower.getFullName();
        }
        return borrower.getPhone() != null ? borrower.getPhone() : borrowerId;
    }

}
