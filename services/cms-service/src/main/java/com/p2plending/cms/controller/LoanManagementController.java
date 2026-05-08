package com.p2plending.cms.controller;

import com.p2plending.cms.dto.request.LoanActionRequest;
import com.p2plending.cms.dto.response.LoanSummaryResponse;
import com.p2plending.cms.dto.response.PagedResponse;
import com.p2plending.cms.service.LoanManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
            @RequestParam(required = false) Long borrowerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(loanService.getLoans(status, borrowerId, page, size));
    }

    @PutMapping("/{loanId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<LoanSummaryResponse> approve(
            @PathVariable Long loanId,
            @Valid @RequestBody LoanActionRequest req) {
        return ResponseEntity.ok(loanService.approve(loanId, req));
    }

    @PutMapping("/{loanId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<LoanSummaryResponse> reject(
            @PathVariable Long loanId,
            @Valid @RequestBody LoanActionRequest req) {
        return ResponseEntity.ok(loanService.reject(loanId, req));
    }
}
