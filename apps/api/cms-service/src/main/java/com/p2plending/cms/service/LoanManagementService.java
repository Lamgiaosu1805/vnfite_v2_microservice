package com.p2plending.cms.service;

import com.p2plending.cms.dto.request.LoanActionRequest;
import com.p2plending.cms.dto.response.LoanSummaryResponse;
import com.p2plending.cms.dto.response.PagedResponse;
import com.p2plending.cms.security.CmsPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LoanManagementService {

    private final SourceServiceClient sourceServiceClient;

    public PagedResponse<LoanSummaryResponse> getLoans(String status, String borrowerId, int page, int size) {
        return sourceServiceClient.getLoans(status, borrowerId, page, size);
    }

    public LoanSummaryResponse approve(String loanId, LoanActionRequest req, CmsPrincipal reviewer) {
        return sourceServiceClient.approveLoan(loanId, req, reviewer != null ? reviewer.username() : null);
    }

    public LoanSummaryResponse reject(String loanId, LoanActionRequest req, CmsPrincipal reviewer) {
        return sourceServiceClient.rejectLoan(loanId, req, reviewer != null ? reviewer.username() : null);
    }
}
