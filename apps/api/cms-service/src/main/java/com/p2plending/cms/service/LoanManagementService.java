package com.p2plending.cms.service;

import com.p2plending.cms.domain.entity.CmsLoan;
import com.p2plending.cms.domain.repository.CmsLoanRepository;
import com.p2plending.cms.dto.request.LoanActionRequest;
import com.p2plending.cms.dto.response.LoanSummaryResponse;
import com.p2plending.cms.dto.response.PagedResponse;
import com.p2plending.cms.exception.ResourceNotFoundException;
import com.p2plending.cms.kafka.CmsKafkaProducerService;
import com.p2plending.cms.kafka.event.LoanReviewedEvent;
import com.p2plending.cms.security.CmsPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class LoanManagementService {

    private final CmsLoanRepository        loanRepo;
    private final CmsKafkaProducerService   kafkaProducer;

    @Transactional(readOnly = true)
    public PagedResponse<LoanSummaryResponse> getLoans(String status, String borrowerId, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return PagedResponse.from(
                loanRepo.findWithFilters(status, borrowerId, pageable).map(this::toResponse));
    }

    @Transactional
    public LoanSummaryResponse approve(String loanId, LoanActionRequest req, CmsPrincipal reviewer) {
        CmsLoan loan = findOrThrow(loanId);

        if (!"PENDING_REVIEW".equals(loan.getStatus())) {
            throw new IllegalStateException("Loan %s is not in PENDING_REVIEW status".formatted(loanId));
        }
        if (req.getInterestRate() == null) {
            throw new IllegalArgumentException("interestRate is required when approving a loan");
        }

        LocalDateTime now = LocalDateTime.now();
        loan.setInterestRate(req.getInterestRate());
        loan.setStatus("ACTIVE");
        loan.setReviewedBy(reviewer.username());
        loan.setReviewedAt(now);
        loanRepo.save(loan);

        kafkaProducer.publishLoanReviewed(LoanReviewedEvent.builder()
                .loanId(loanId)
                .action("APPROVE")
                .interestRate(req.getInterestRate())
                .reviewedBy(reviewer.username())
                .reviewedAt(now)
                .build());

        return toResponse(loan);
    }

    @Transactional
    public LoanSummaryResponse reject(String loanId, LoanActionRequest req, CmsPrincipal reviewer) {
        CmsLoan loan = findOrThrow(loanId);

        if (!"PENDING_REVIEW".equals(loan.getStatus())) {
            throw new IllegalStateException("Loan %s is not in PENDING_REVIEW status".formatted(loanId));
        }

        LocalDateTime now = LocalDateTime.now();
        loan.setRejectionReason(req.getReason());
        loan.setStatus("REJECTED");
        loan.setReviewedBy(reviewer.username());
        loan.setReviewedAt(now);
        loanRepo.save(loan);

        kafkaProducer.publishLoanReviewed(LoanReviewedEvent.builder()
                .loanId(loanId)
                .action("REJECT")
                .rejectionReason(req.getReason())
                .reviewedBy(reviewer.username())
                .reviewedAt(now)
                .build());

        return toResponse(loan);
    }

    private CmsLoan findOrThrow(String id) {
        return loanRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Loan not found: " + id));
    }

    private LoanSummaryResponse toResponse(CmsLoan l) {
        return LoanSummaryResponse.builder()
                .loanId(l.getLoanId())
                .loanCode(l.getLoanCode())
                .borrowerId(l.getBorrowerId())
                .amount(l.getAmount())
                .interestRate(l.getInterestRate())
                .termMonths(l.getTermMonths())
                .purpose(l.getPurpose())
                .occupation(l.getOccupation())
                .monthlyIncome(l.getMonthlyIncome())
                .currentAddress(l.getCurrentAddress())
                .referredBy(l.getReferredBy())
                .status(l.getStatus())
                .rejectionReason(l.getRejectionReason())
                .reviewedBy(l.getReviewedBy())
                .reviewedAt(l.getReviewedAt())
                .createdAt(l.getCreatedAt())
                .build();
    }
}
