package com.p2plending.cms.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanOfferSummaryResponse {
    private String offerId;
    private String investorId;
    private String investorName;
    private String investorPhone;
    private BigDecimal amount;
    private String status;
    private LocalDateTime createdAt;
}
