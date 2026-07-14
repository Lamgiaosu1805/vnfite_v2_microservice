package com.p2plending.cms.controller;

import com.p2plending.cms.dto.response.CustomerLoanViewResponse;
import com.p2plending.cms.dto.response.LoanSummaryResponse;
import com.p2plending.cms.service.LoanManagementService;
import lombok.RequiredArgsConstructor;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;

/** Read-only loan progress for customer-support staff from a customer's profile. */
@RestController
@RequestMapping("/cms/users/{userId}/customer-loans")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'CUSTOMER_SUPPORT')")
public class CustomerLoanViewController {

    private final LoanManagementService loanService;

    @GetMapping("/{loanId}")
    public ResponseEntity<CustomerLoanViewResponse> getLoan(
            @PathVariable String userId, @PathVariable String loanId) {
        LoanSummaryResponse loan = requireBorrowerLoan(userId, loanId);
        int confirmedInvestorCount = loan.getOffers() == null ? 0 : (int) loan.getOffers().stream()
                .filter(offer -> "ACCEPTED".equalsIgnoreCase(offer.getStatus()))
                .count();

        return ResponseEntity.ok(new CustomerLoanViewResponse(
                loan.getLoanId(), loan.getLoanCode(), loan.getProductName(), loan.getProductCategory(),
                loan.getBusinessName(), loan.getAmount(), loan.getFundedAmount(), confirmedInvestorCount,
                loan.getInterestRate(), loan.getTermMonths(), loan.getPurpose(), loan.getStatus(),
                loan.getRejectionReason(), loan.getCreatedAt(), loan.getReviewedAt()));
    }

    /** Chứng từ khoản gọi vốn chỉ để xem, theo đúng khách hàng đang mở trong CMS. */
    @GetMapping("/{loanId}/documents")
    public ResponseEntity<JsonNode> getDocuments(
            @PathVariable String userId, @PathVariable String loanId) {
        requireBorrowerLoan(userId, loanId);
        return ResponseEntity.ok(loanService.getDocuments(loanId));
    }

    private LoanSummaryResponse requireBorrowerLoan(String userId, String loanId) {
        LoanSummaryResponse loan = loanService.getLoan(loanId);
        if (!Objects.equals(userId, loan.getBorrowerId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy khoản gọi vốn của khách hàng");
        }
        return loan;
    }
}
