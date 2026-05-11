package com.p2plending.cms.service;

import com.p2plending.cms.domain.entity.CmsLoan;
import com.p2plending.cms.domain.repository.CmsLoanRepository;
import com.p2plending.cms.dto.request.LoanActionRequest;
import com.p2plending.cms.dto.response.LoanSummaryResponse;
import com.p2plending.cms.dto.response.PagedResponse;
import com.p2plending.cms.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LoanManagementService {

    private final CmsLoanRepository loanRepo;

    @Transactional(readOnly = true)
    public PagedResponse<LoanSummaryResponse> getLoans(String status, String borrowerId, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return PagedResponse.from(
                loanRepo.findWithFilters(status, borrowerId, pageable).map(this::toResponse));
    }

    @Transactional
    public LoanSummaryResponse approve(String loanId, LoanActionRequest req) {
        CmsLoan loan = findOrThrow(loanId);
        loan.setStatus("ACTIVE");
        return toResponse(loanRepo.save(loan));
    }

    @Transactional
    public LoanSummaryResponse reject(String loanId, LoanActionRequest req) {
        CmsLoan loan = findOrThrow(loanId);
        loan.setStatus("REJECTED");
        return toResponse(loanRepo.save(loan));
    }

    private CmsLoan findOrThrow(String id) {
        return loanRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Loan not found: " + id));
    }

    private LoanSummaryResponse toResponse(CmsLoan l) {
        return LoanSummaryResponse.builder()
                .loanId(l.getLoanId()).borrowerId(l.getBorrowerId()).amount(l.getAmount())
                .interestRate(l.getInterestRate()).termMonths(l.getTermMonths())
                .purpose(l.getPurpose()).status(l.getStatus()).createdAt(l.getCreatedAt())
                .build();
    }
}
