package com.p2plending.payment.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class WalletResponse {
    private String walletId;
    private String ownerType;
    private String vnfAccountNo;
    private BigDecimal totalBalance;
    private BigDecimal lockedBalance;
    private BigDecimal availableBalance;
    private LocalDateTime createdAt;
}
