package com.p2plending.loan.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Báo cáo sổ cái doanh thu phí — tổng + danh sách phân trang cho CMS. */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class FeeRevenueReportResponse {

    private long totalCount;
    private BigDecimal totalAppraisalFee;
    private BigDecimal totalVat;
    private BigDecimal totalFeeRevenue;
    private int page;
    private int size;
    private int totalPages;
    private List<Item> items;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Item {
        private String loanId;
        private String loanCode;
        private String borrowerId;
        private BigDecimal loanAmount;
        private BigDecimal appraisalFeeRate;
        private BigDecimal appraisalFee;
        private BigDecimal vatAmount;
        private BigDecimal totalFee;
        private LocalDateTime disbursedAt;
        private String disbursedBy;
    }
}
