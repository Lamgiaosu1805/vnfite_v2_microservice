package com.p2plending.cms.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.p2plending.cms.dto.request.CicLookupRequest;
import com.p2plending.cms.dto.request.LoanActionRequest;
import com.p2plending.cms.dto.request.LoanProposeRequest;
import com.p2plending.cms.dto.response.CicLookupResponse;
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

import java.util.Map;

@RestController
@RequestMapping("/cms/loans")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'OPS')")
public class LoanManagementController {

    private final LoanManagementService loanService;

    @GetMapping("/products")
    public ResponseEntity<JsonNode> getLoanProducts() {
        return ResponseEntity.ok(loanService.getLoanProducts());
    }

    @PutMapping("/products/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<JsonNode> updateLoanProduct(
            @PathVariable String id,
            @RequestBody java.util.Map<String, Object> body) {
        return ResponseEntity.ok(loanService.updateLoanProduct(id, body));
    }

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
            @RequestParam(defaultValue = "false") boolean discouraged,
            @RequestParam(required = false) String creditGrade) {
        return ResponseEntity.ok(loanService.getAppraisalSuggestion(loanId, discouraged, creditGrade));
    }

    /** GET /cms/loans/{loanId}/repayments — lịch trả nợ để theo dõi DPD. */
    @GetMapping("/{loanId}/repayments")
    public ResponseEntity<JsonNode> getRepaymentSchedule(@PathVariable String loanId) {
        return ResponseEntity.ok(loanService.getRepaymentSchedule(loanId));
    }

    /** GET /cms/loans/{loanId}/contracts — hợp đồng vay + đầu tư của khoản. */
    @GetMapping("/{loanId}/contracts")
    public ResponseEntity<JsonNode> getContracts(@PathVariable String loanId) {
        return ResponseEntity.ok(loanService.getContracts(loanId));
    }

    /** GET /cms/loans/{loanId}/documents — chứng từ người gọi vốn đã nộp. */
    @GetMapping("/{loanId}/documents")
    public ResponseEntity<JsonNode> getDocuments(@PathVariable String loanId) {
        return ResponseEntity.ok(loanService.getDocuments(loanId));
    }

    /** POST /cms/loans/{loanId}/credit-score — chấm điểm tín dụng tham khảo. */
    @PostMapping("/{loanId}/credit-score")
    public ResponseEntity<JsonNode> evaluateCreditScore(@PathVariable String loanId) {
        return ResponseEntity.ok(loanService.evaluateCreditScore(loanId));
    }

    /** GET /cms/loans/{loanId}/credit-score — lấy kết quả chấm điểm gần nhất đã lưu. */
    @GetMapping("/{loanId}/credit-score")
    public ResponseEntity<JsonNode> getLatestCreditScore(@PathVariable String loanId) {
        return ResponseEntity.ok(loanService.getLatestCreditScore(loanId));
    }

    /** GET /cms/loans/{loanId}/cic — kết quả tra CIC nhập tay mới nhất (null nếu chưa nhập). */
    @GetMapping("/{loanId}/cic")
    public ResponseEntity<CicLookupResponse> getCicLookup(@PathVariable String loanId) {
        return ResponseEntity.ok(loanService.getCicLookup(loanId));
    }

    /**
     * POST /cms/loans/{loanId}/cic — thẩm định viên nhập kết quả tra CIC bên ngoài.
     * Chờ API CIC sandbox NĐ94: nhập tay để chấm nhóm B + lưu audit. Cho phép cả OPS.
     */
    @PostMapping("/{loanId}/cic")
    public ResponseEntity<CicLookupResponse> saveCicLookup(
            @PathVariable String loanId,
            @Valid @RequestBody CicLookupRequest req,
            @AuthenticationPrincipal CmsPrincipal operator) {
        return ResponseEntity.ok(loanService.saveCicLookup(loanId, req, operator));
    }

    /** POST /cms/loans/{loanId}/documents/{documentId}/analyze — AI phân tích chứng từ. */
    @PostMapping("/{loanId}/documents/{documentId}/analyze")
    public ResponseEntity<JsonNode> analyzeDocument(
            @PathVariable String loanId,
            @PathVariable String documentId) {
        return ResponseEntity.ok(loanService.analyzeDocument(loanId, documentId));
    }

    /**
     * POST /cms/loans/expire-sweep
     * Chạy ngay job hết hạn gọi vốn (ACTIVE quá hạn) và hết hạn ký khế ước (FUNDED kẹt):
     * hoàn tiền nhà đầu tư + chuyển khoản sang CANCELLED. Dùng cho vận hành/test thay vì chờ cron.
     */
    @PostMapping("/expire-sweep")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<JsonNode> expireSweep() {
        return ResponseEntity.ok(loanService.expireSweep());
    }

    /**
     * POST /cms/loans/{loanId}/disburse
     * OPS giải ngân vốn cho người gọi vốn: AWAITING_DISBURSEMENT → DISBURSED.
     * Sinh lịch trả nợ từ ngày giải ngân + bắn loan.disbursed.
     */
    @PostMapping("/{loanId}/disburse")
    public ResponseEntity<LoanSummaryResponse> disburse(
            @PathVariable String loanId,
            @AuthenticationPrincipal CmsPrincipal operator) {
        return ResponseEntity.ok(loanService.disburse(loanId, operator));
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
     * Publishes loan.reviewed (APPROVE) → loan-service sets AWAITING_BORROWER_APPROVAL.
     * Borrower must accept the approved terms before the loan becomes ACTIVE on the marketplace.
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

    // ── Fee Config ────────────────────────────────────────────────────────────

    @GetMapping("/fee-config")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<JsonNode> getFeeConfig() {
        return ResponseEntity.ok(loanService.getFeeConfigs());
    }

    @PutMapping("/fee-config")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<JsonNode> updateFeeConfig(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal CmsPrincipal admin) {
        return ResponseEntity.ok(loanService.upsertFeeConfig(body, admin.username()));
    }
}
