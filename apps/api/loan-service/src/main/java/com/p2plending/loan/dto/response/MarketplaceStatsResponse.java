package com.p2plending.loan.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketplaceStatsResponse {
    private long activeLoanCount;
    private BigDecimal activeFundingVolume;
}
