package com.p2plending.cms.dto.response;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data @Builder @NoArgsConstructor
public class ChartDataResponse {
    private List<DataPoint> points;

    @Data @Builder @NoArgsConstructor
    public static class DataPoint {
        private LocalDate date;
        private long newUsers;
        private long newLoans;
        private long fundedLoans;
        private BigDecimal loanVolume;
    }
}
