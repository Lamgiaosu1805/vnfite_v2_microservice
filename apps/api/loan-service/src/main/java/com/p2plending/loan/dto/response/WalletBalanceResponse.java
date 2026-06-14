package com.p2plending.loan.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Số dư ví nhà đầu tư lấy từ payment-service ({@code GET /internal/payment/wallet/{userId}}).
 * payment-service trả raw JSON (không bọc envelope) nên map trực tiếp.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WalletBalanceResponse {
    private String walletId;
    private String vnfAccountNo;
    private BigDecimal totalBalance;
    private BigDecimal lockedBalance;
    private BigDecimal availableBalance;
}
