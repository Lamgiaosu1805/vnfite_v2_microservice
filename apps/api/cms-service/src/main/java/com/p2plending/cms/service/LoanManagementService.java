package com.p2plending.cms.service;

import com.p2plending.cms.dto.request.LoanActionRequest;
import com.p2plending.cms.dto.response.LoanSummaryResponse;
import com.p2plending.cms.dto.response.PagedResponse;
import com.p2plending.cms.security.CmsPrincipal;
import org.springframework.stereotype.Service;

@Service
public class LoanManagementService {

    public PagedResponse<LoanSummaryResponse> getLoans(String status, String borrowerId, int page, int size) {
        return PagedResponse.empty(page, size);
    }

    public LoanSummaryResponse approve(String loanId, LoanActionRequest req, CmsPrincipal reviewer) {
        throw new UnsupportedOperationException("CMS no longer stores loan mirror data. Send review decisions to loan-service.");
    }

    public LoanSummaryResponse reject(String loanId, LoanActionRequest req, CmsPrincipal reviewer) {
        throw new UnsupportedOperationException("CMS no longer stores loan mirror data. Send review decisions to loan-service.");
    }
}
