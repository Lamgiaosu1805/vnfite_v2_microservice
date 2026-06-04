package com.p2plending.loan.controller;

import com.p2plending.loan.dto.request.InternalLoanReviewRequest;
import com.p2plending.loan.dto.request.LoanFilterParams;
import com.p2plending.loan.dto.response.InternalLoanStatsResponse;
import com.p2plending.loan.dto.response.LoanResponse;
import com.p2plending.loan.dto.response.PagedResponse;
import com.p2plending.loan.service.LoanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

@RestController
@RequestMapping("/internal/loans")
@RequiredArgsConstructor
public class InternalLoanController {

    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";

    private final LoanService loanService;

    @Value("${app.internal.secret:dev-internal-secret}")
    private String internalSecret;

    @GetMapping
    public ResponseEntity<PagedResponse<LoanResponse>> getLoans(
            @RequestHeader(INTERNAL_SECRET_HEADER) String secret,
            @Valid LoanFilterParams params) {
        requireInternalSecret(secret);
        return ResponseEntity.ok(loanService.getLoans(params));
    }

    @GetMapping("/{loanId}")
    public ResponseEntity<LoanResponse> getLoan(
            @RequestHeader(INTERNAL_SECRET_HEADER) String secret,
            @PathVariable String loanId) {
        requireInternalSecret(secret);
        return ResponseEntity.ok(loanService.getLoanById(loanId));
    }

    /** Thống kê tổng hợp — dùng cho CMS dashboard */
    @GetMapping("/stats")
    public ResponseEntity<InternalLoanStatsResponse> getStats(
            @RequestHeader(INTERNAL_SECRET_HEADER) String secret,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from) {
        requireInternalSecret(secret);
        return ResponseEntity.ok(loanService.getLoanStats(from));
    }

    @PutMapping("/{loanId}/approve")
    public ResponseEntity<LoanResponse> approveLoan(
            @RequestHeader(INTERNAL_SECRET_HEADER) String secret,
            @PathVariable String loanId,
            @Valid @RequestBody InternalLoanReviewRequest request) {
        requireInternalSecret(secret);
        return ResponseEntity.ok(loanService.reviewLoan(loanId, "APPROVE", request.getInterestRate(), null, request.getReviewedBy()));
    }

    @PutMapping("/{loanId}/reject")
    public ResponseEntity<LoanResponse> rejectLoan(
            @RequestHeader(INTERNAL_SECRET_HEADER) String secret,
            @PathVariable String loanId,
            @Valid @RequestBody InternalLoanReviewRequest request) {
        requireInternalSecret(secret);
        return ResponseEntity.ok(loanService.reviewLoan(loanId, "REJECT", null, request.getReason(), request.getReviewedBy()));
    }

    private void requireInternalSecret(String secret) {
        if (!internalSecret.equals(secret)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid internal secret");
        }
    }
}
