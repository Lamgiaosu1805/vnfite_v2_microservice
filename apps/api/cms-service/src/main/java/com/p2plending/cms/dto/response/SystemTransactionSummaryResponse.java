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
public class SystemTransactionSummaryResponse {
    private String id;
    private String userId;
    private String customerName;
    private String customerPhone;
    private String walletId;
    private String vnfAccountNo;
    private String type;
    private BigDecimal amount;
    private String status;
    private String description;
    private String referenceId;
    private String externalRef;
    private BigDecimal balanceAfter;
    private LocalDateTime createdAt;
}
