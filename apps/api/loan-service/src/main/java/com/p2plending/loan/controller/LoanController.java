package com.p2plending.loan.controller;

import com.p2plending.loan.dto.request.LoanCreateRequest;
import com.p2plending.loan.dto.request.LoanFilterParams;
import com.p2plending.loan.dto.request.LoanOfferCreateRequest;
import com.p2plending.loan.dto.response.LoanOfferResponse;
import com.p2plending.loan.dto.response.LoanResponse;
import com.p2plending.loan.dto.response.PagedResponse;
import com.p2plending.loan.security.AuthenticatedUser;
import com.p2plending.loan.service.LoanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/loans")
@RequiredArgsConstructor
public class LoanController {

    private final LoanService loanService;

    /**
     * POST /api/loans/request
     * Borrower submits a new loan application (gọi vốn).
     * Status starts as PENDING_REVIEW — loan is NOT visible on marketplace until CMS approves.
     * Publishes "loan.submitted" Kafka event for CMS underwriting queue.
     */
    @PostMapping("/request")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<LoanResponse> createLoan(
            @Valid @RequestBody LoanCreateRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(loanService.createLoan(request, principal.userId()));
    }

    /**
     * GET /api/loans
     * Paginated loan list. Investors see ACTIVE loans by default.
     * Query params: status, borrowerId, minAmount, maxAmount, page, size, sortBy, sortDir
     */
    @GetMapping
    public ResponseEntity<PagedResponse<LoanResponse>> getLoans(
            @Valid LoanFilterParams params
    ) {
        return ResponseEntity.ok(loanService.getLoans(params));
    }

    /**
     * GET /api/loans/{id}
     * Fetches a single loan with its offers. Cached 10 minutes.
     */
    @GetMapping("/{id}")
    public ResponseEntity<LoanResponse> getLoanById(@PathVariable String id) {
        return ResponseEntity.ok(loanService.getLoanById(id));
    }

    /**
     * POST /api/loans/{id}/offer
     * Investor places an offer on an ACTIVE loan.
     * Publishes "loan.funded" when fully funded.
     */
    @PostMapping("/{id}/offer")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<LoanOfferResponse> createOffer(
            @PathVariable String id,
            @Valid @RequestBody LoanOfferCreateRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(loanService.createOffer(id, request, principal.userId()));
    }
}
