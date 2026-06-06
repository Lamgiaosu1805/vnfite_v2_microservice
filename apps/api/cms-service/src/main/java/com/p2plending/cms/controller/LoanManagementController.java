package com.p2plending.cms.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.p2plending.cms.dto.request.LoanActionRequest;
import com.p2plending.cms.dto.request.LoanProposeRequest;
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
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'OPS')")
public class LoanManagementController {

    private final LoanManagementService loanService;

    @GetMapping
    public ResponseEntity<PagedResponse<LoanSummaryResponse>> getLoans(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String borrowerId,
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(loanService.getLoans(status, borrowerId, province, search, page, size));
    }

    /**
     * GET /cms/loans/{loanId}/appraisal-suggestion
     * Gợi ý hỗ trợ thẩm định: điểm/hạng tín nhiệm, năng lực trả nợ, số tiền & lãi suất + phí
     * đề xuất (biểu QĐ-LSGV), xem trước lịch trả nợ, checklist thẩm định thủ công.
     * Cho phép cả OPS (thẩm định viên) xem.
     */
    @GetMapping("/{loanId}/appraisal-suggestion")
    public ResponseEntity<JsonNode> getAppraisalSuggestion(
            @PathVariable String loanId,
            @RequestParam(defaultValue = "false") boolean discouraged) {
        return ResponseEntity.ok(loanService.getAppraisalSuggestion(loanId, discouraged));
    }

    /** GET /cms/loans/{loanId}/repayments — lịch trả nợ để theo dõi DPD. */
    @GetMapping("/{loanId}/repayments")
    public ResponseEntity<JsonNode> getRepaymentSchedule(@PathVariable String loanId) {
        return ResponseEntity.ok(loanService.getRepaymentSchedule(loanId));
    }

    /**
     * PUT /cms/loans/{loanId}/propose
     * Cấp 1 — thẩm định viên (OPS) đề xuất số tiền + lãi suất → trình ban lãnh đạo.
     * PENDING_REVIEW → PENDING_APPROVAL.
     */
    @PutMapping("/{loanId}/propose")
    public ResponseEntity<LoanSummaryResponse> propose(
            @PathVariable String loanId,
            @Valid @RequestBody LoanProposeRequest req,
            @AuthenticationPrincipal CmsPrincipal proposer) {
        return ResponseEntity.ok(loanService.propose(loanId, req, proposer));
    }

    /**
     * PUT /cms/loans/{loanId}/approve
     * Cấp 2 — ban lãnh đạo (ADMIN) duyệt, có thể sửa lãi suất trước khi duyệt.
     * Publishes loan.reviewed (APPROVE) → loan-service sets ACTIVE and triggers matching.
     */
    @PutMapping("/{loanId}/approve")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
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
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<LoanSummaryResponse> reject(
            @PathVariable String loanId,
            @Valid @RequestBody LoanActionRequest req,
            @AuthenticationPrincipal CmsPrincipal reviewer) {
        return ResponseEntity.ok(loanService.reject(loanId, req, reviewer));
    }
}
