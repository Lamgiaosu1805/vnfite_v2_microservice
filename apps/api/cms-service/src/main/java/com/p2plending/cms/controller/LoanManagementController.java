package com.p2plending.cms.controller;

import com.p2plending.cms.dto.request.LoanActionRequest;
import com.p2plending.cms.dto.response.LoanSummaryResponse;
import com.p2plending.cms.dto.response.PagedResponse;
import com.p2plending.cms.security.CmsPrincipal;
import com.p2plending.cms.service.LoanManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/cms/loans")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
public class LoanManagementController {

    private final LoanManagementService loanService;

    @GetMapping
    public ResponseEntity<PagedResponse<LoanSummaryResponse>> getLoans(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String borrowerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(loanService.getLoans(status, borrowerId, page, size));
    }

    /**
     * PUT /cms/loans/{loanId}/approve
     * CMS admin approves a PENDING_REVIEW loan and sets the interest rate.
     * Publishes loan.reviewed (APPROVE) → loan-service sets ACTIVE and triggers matching.
     */
    @PutMapping("/{loanId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<LoanSummaryResponse> approve(
            @PathVariable String loanId,
            @Valid @RequestBody LoanActionRequest req,
            @AuthenticationPrincipal CmsPrincipal reviewer) {
        return ResponseEntity.ok(loanService.approve(loanId, req, reviewer));
    }

    /**
     * PUT /cms/loans/{loanId}/reject
     * CMS admin rejects a PENDING_REVIEW loan with a reason.
     * Publishes loan.reviewed (REJECT) → loan-service sets REJECTED.
     */
    @PutMapping("/{loanId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<LoanSummaryResponse> reject(
            @PathVariable String loanId,
            @Valid @RequestBody LoanActionRequest req,
            @AuthenticationPrincipal CmsPrincipal reviewer) {
        return ResponseEntity.ok(loanService.reject(loanId, req, reviewer));
    }
}
