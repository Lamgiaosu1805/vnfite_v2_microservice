package com.p2plending.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class InternalUserStatsResponse {
    private long totalUsers;
    private long pendingKyc;
    private long newUsersToday;
    private List<DailyCount> dailyCounts;

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class DailyCount {
        private LocalDate date;
        private long count;
    }
}
