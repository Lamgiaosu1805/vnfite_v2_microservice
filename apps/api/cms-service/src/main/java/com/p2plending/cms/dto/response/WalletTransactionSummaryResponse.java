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
public class WalletTransactionSummaryResponse {
    private String id;
    private String type;
    private BigDecimal amount;
    private String status;
    private String description;
    private BigDecimal balanceAfter;
    private LocalDateTime createdAt;
}
