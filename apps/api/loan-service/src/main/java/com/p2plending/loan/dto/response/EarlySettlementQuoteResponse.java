package com.p2plending.loan.dto.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Báo giá / kết quả tất toán trước hạn.
 *
 * <pre>
 * totalPayoff = remainingPrincipal + interestToDate + penaltyOutstanding + settlementFee
 * </pre>
 * settlementFee = settlementFeeRate% × remainingPrincipal (về VNFITE).
 */
@Builder
public record EarlySettlementQuoteResponse(
        String loanId,
        String loanCode,
        LocalDate asOfDate,
        BigDecimal remainingPrincipal,
        BigDecimal interestToDate,
        BigDecimal penaltyOutstanding,
        BigDecimal settlementFeeRate,
        BigDecimal settlementFee,
        BigDecimal totalPayoff,
        boolean settled
) {}
