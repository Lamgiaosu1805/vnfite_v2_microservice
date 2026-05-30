package com.p2plending.cms.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data @Builder
public class ChartDataResponse {
    private List<DataPoint> points;

    @Data @Builder
    public static class DataPoint {
        private LocalDate date;
        private long newUsers;
        private long newLoans;
        private long fundedLoans;
        private BigDecimal loanVolume;
    }
}
