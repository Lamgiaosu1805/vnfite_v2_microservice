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
public class WalletSummaryResponse {
    private String walletId;
    private String vnfAccountNo;
    private BigDecimal totalBalance;
    private BigDecimal lockedBalance;
    private BigDecimal availableBalance;
    private LocalDateTime createdAt;
}
