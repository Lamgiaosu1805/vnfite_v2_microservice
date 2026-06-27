package com.p2plending.cms.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerDetailResponse {
    private UserSummaryResponse profile;
    private WalletSummaryResponse wallet;
    private PagedResponse<WalletTransactionSummaryResponse> transactions;
    private PagedResponse<LoanSummaryResponse> loans;
    private InvestorCashflowResponse investments;
}
