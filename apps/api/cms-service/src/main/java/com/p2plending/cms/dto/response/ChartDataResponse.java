package com.p2plending.cms.dto.response;

import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ChartDataResponse {
    private List<DataPoint> points;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DataPoint {
        private LocalDate date;
        /** Nhãn hiển thị trên trục X: "T2"…"CN" / "1"…"31" / "T1"…"T12" */
        private String label;
        private long newUsers;
        private long newLoans;
        private long fundedLoans;
        private BigDecimal loanVolume;
        /** true = ngày trong tương lai, chưa có data — frontend hiển thị mờ */
        private boolean future;
    }
}
