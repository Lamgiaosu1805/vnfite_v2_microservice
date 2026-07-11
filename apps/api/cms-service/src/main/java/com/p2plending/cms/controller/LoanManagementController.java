package com.p2plending.cms.controller;

import com.p2plending.cms.dto.request.CicLookupRequest;
import com.p2plending.cms.dto.request.BusinessAppraisalChecklistRequest;
import com.p2plending.cms.dto.request.LoanActionRequest;
import com.p2plending.cms.dto.request.LoanProposeRequest;
import com.p2plending.cms.dto.request.RecordRepaymentRequest;
import com.p2plending.cms.dto.response.BusinessAppraisalChecklistResponse;
import com.p2plending.cms.dto.response.CicLookupResponse;
import com.p2plending.cms.dto.response.LoanSummaryResponse;
import com.p2plending.cms.dto.response.PagedResponse;
import com.p2plending.cms.security.CmsPrincipal;
import com.p2plending.cms.service.LoanManagementService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/cms/loans")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'OPS', 'APPRAISER', 'APPROVER', 'FINANCE')")
public class LoanManagementController {

    private final LoanManagementService loanService;

    @GetMapping("/products")
    public ResponseEntity<JsonNode> getLoanProducts() {
        return ResponseEntity.ok(loanService.getLoanProducts());
    }

    @PutMapping("/products/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'APPRAISER', 'APPROVER') or hasAuthority('loan.product.edit')")
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
            @RequestParam(required = false) String productCategories,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(loanService.getLoans(status, borrowerId, province, search, productCategories, page, size));
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

    /** GET /cms/loans/{loanId}/business-appraisal — checklist thẩm định riêng HKD/DN đã lưu. */
    @GetMapping("/{loanId}/business-appraisal")
    public ResponseEntity<List<BusinessAppraisalChecklistResponse>> getBusinessAppraisalChecklist(
            @PathVariable String loanId) {
        return ResponseEntity.ok(loanService.getBusinessAppraisalChecklist(loanId));
    }

    /** PUT /cms/loans/{loanId}/business-appraisal/{code} — lưu kết quả checklist HKD/DN. */
    @PutMapping("/{loanId}/business-appraisal/{code}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'APPRAISER', 'APPROVER') or hasAuthority('loan.propose') or hasAuthority('loan.approve')")
    public ResponseEntity<BusinessAppraisalChecklistResponse> saveBusinessAppraisalChecklist(
            @PathVariable String loanId,
            @PathVariable String code,
            @Valid @RequestBody BusinessAppraisalChecklistRequest req,
            @AuthenticationPrincipal CmsPrincipal operator) {
        return ResponseEntity.ok(loanService.saveBusinessAppraisalChecklist(loanId, code, req, operator));
    }

    /**
     * POST /cms/loans/expire-sweep
     * Chạy ngay job hết hạn gọi vốn (ACTIVE quá hạn) và hết hạn ký khế ước (FUNDED kẹt):
     * hoàn tiền nhà đầu tư + chuyển khoản sang CANCELLED. Dùng cho vận hành/test thay vì chờ cron.
     */
    @PostMapping("/expire-sweep")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'APPRAISER', 'APPROVER')")
    public ResponseEntity<JsonNode> expireSweep() {
        return ResponseEntity.ok(loanService.expireSweep());
    }

    /**
     * POST /cms/loans/repayments/auto-debit-sweep
     * Chạy ngay job thu nợ tự động từ ví người gọi vốn. Dùng khi khách vừa nạp tiền sau lượt cron.
     */
    @PostMapping("/repayments/auto-debit-sweep")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'APPRAISER', 'APPROVER')")
    public ResponseEntity<JsonNode> autoDebitSweep(@AuthenticationPrincipal CmsPrincipal operator) {
        return ResponseEntity.ok(loanService.autoDebitSweep(operator));
    }

    /**
     * GET /cms/loans/repayments/due-today?date=yyyy-MM-dd
     * Kỳ trả nợ đến hạn theo ngày — theo dõi ai đã/chưa trả.
     */
    @GetMapping("/repayments/due-today")
    public ResponseEntity<com.fasterxml.jackson.databind.JsonNode> getDueTodaySchedules(
            @RequestParam(required = false) String date) {
        return ResponseEntity.ok(loanService.getDueTodaySchedules(date));
    }

    /**
     * GET /cms/loans/repayments/auto-debit-audit
     * Lịch sử các lần quét auto-debit — giám sát kết quả scheduler hàng ngày.
     */
    @GetMapping("/repayments/auto-debit-audit")
    public ResponseEntity<com.fasterxml.jackson.databind.JsonNode> getAutoDebitAudit(
            @RequestParam(defaultValue = "200") int limit) {
        return ResponseEntity.ok(loanService.getAutoDebitAudit(limit));
    }

    /**
     * GET /cms/loans/repayments/auto-debit-audit/{auditId}/items
     * Chi tiết từng khoản trong một lần quét auto-debit.
     */
    @GetMapping("/repayments/auto-debit-audit/{auditId}/items")
    public ResponseEntity<com.fasterxml.jackson.databind.JsonNode> getAutoDebitAuditItems(
            @PathVariable String auditId) {
        return ResponseEntity.ok(loanService.getAutoDebitAuditItems(auditId));
    }

    /**
     * GET /cms/loans/repayments/distribution-log
     * Log phân bổ nhà đầu tư: gốc/lãi/phí phạt/thuế TNCN/net — kế toán tra soát.
     */
    @GetMapping("/repayments/distribution-log")
    public ResponseEntity<com.fasterxml.jackson.databind.JsonNode> getDistributionLog(
            @RequestParam(required = false) String loanId,
            @RequestParam(required = false) String investorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(loanService.getDistributionLog(loanId, investorId, page, size));
    }

    /**
     * GET /cms/loans/early-settlements?page=0&size=20
     * Sổ tất toán trước hạn — CMS đối soát doanh thu phí tất toán.
     */
    @GetMapping("/early-settlements")
    public ResponseEntity<com.fasterxml.jackson.databind.JsonNode> getEarlySettlements(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(loanService.getEarlySettlements(page, size));
    }

    /**
     * GET /cms/loans/{loanId}/early-settlement/quote
     * Admin/ops xem trước báo giá tất toán sớm của 1 khoản — chỉ xem, không trừ tiền,
     * không đổi trạng thái khoản.
     */
    @GetMapping("/{loanId}/early-settlement/quote")
    public ResponseEntity<com.fasterxml.jackson.databind.JsonNode> getEarlySettlementQuote(
            @PathVariable String loanId) {
        return ResponseEntity.ok(loanService.getEarlySettlementQuote(loanId));
    }

    /**
     * GET /cms/loans/stats/fee-revenue
     * Sổ cái doanh thu phí thẩm định + VAT (khoản đã giải ngân) — kế toán tra soát.
     */
    @GetMapping("/stats/fee-revenue")
    public ResponseEntity<com.fasterxml.jackson.databind.JsonNode> getFeeRevenue(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(loanService.getFeeRevenueReport(page, size));
    }

    /**
     * POST /cms/loans/{loanId}/disburse
     * ADMIN giải ngân vốn cho người gọi vốn: AWAITING_DISBURSEMENT → DISBURSED.
     * Sinh lịch trả nợ từ ngày giải ngân + bắn loan.disbursed.
     */
    @PostMapping("/{loanId}/disburse")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'APPRAISER', 'APPROVER') or hasAuthority('loan.disburse')")
    public ResponseEntity<LoanSummaryResponse> disburse(
            @PathVariable String loanId,
            @AuthenticationPrincipal CmsPrincipal operator) {
        return ResponseEntity.ok(loanService.disburse(loanId, operator));
    }

    @PostMapping("/contracts/{contractId}/confirm-paper-signature")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'OPS') or hasAuthority('loan.disburse')")
    public ResponseEntity<com.fasterxml.jackson.databind.JsonNode> confirmPaperSignature(
            @PathVariable String contractId, @AuthenticationPrincipal CmsPrincipal operator) {
        return ResponseEntity.ok(loanService.confirmPaperSignature(contractId, operator));
    }

    @PostMapping("/{loanId}/confirm-all-paper-signatures")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'OPS') or hasAuthority('loan.disburse')")
    public ResponseEntity<com.fasterxml.jackson.databind.JsonNode> confirmAllPaperSignatures(
            @PathVariable String loanId, @AuthenticationPrincipal CmsPrincipal operator) {
        return ResponseEntity.ok(loanService.confirmAllPaperSignatures(loanId, operator));
    }

    /**
     * POST /cms/loans/{loanId}/repayments
     * Admin ghi nhận một lần trả nợ thủ công khi khách trả tiền mặt / chuyển khoản ngoài ví VNFITE.
     * Tiền áp vào kỳ sớm nhất chưa trả (gốc+lãi trước, dư trả phí phạt). Trả về lịch trả nợ mới.
     */
    @PostMapping("/{loanId}/repayments")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'APPRAISER', 'APPROVER')")
    public ResponseEntity<JsonNode> recordRepayment(
            @PathVariable String loanId,
            @Valid @RequestBody RecordRepaymentRequest req,
            @AuthenticationPrincipal CmsPrincipal operator) {
        return ResponseEntity.ok(loanService.recordRepayment(loanId, req, operator));
    }

    /**
     * PUT /cms/loans/{loanId}/propose
     * Cấp 1 — thẩm định viên (OPS) đề xuất số tiền + lãi suất → trình ban lãnh đạo.
     * PENDING_REVIEW → PENDING_APPROVAL.
     */
    @PutMapping("/{loanId}/propose")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'APPRAISER', 'APPROVER') or hasAuthority('loan.propose')")
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
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'APPRAISER', 'APPROVER') or hasAuthority('loan.approve')")
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
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'APPRAISER', 'APPROVER')")
    public ResponseEntity<LoanSummaryResponse> reject(
            @PathVariable String loanId,
            @Valid @RequestBody LoanActionRequest req,
            @AuthenticationPrincipal CmsPrincipal reviewer) {
        return ResponseEntity.ok(loanService.reject(loanId, req, reviewer));
    }

    /**
     * PUT /cms/loans/{loanId}/cancel
     * CMS hủy khoản gọi vốn trước khi giải ngân. Nếu đã có nhà đầu tư, loan-service hoàn tiền/void hợp đồng.
     */
    @PutMapping("/{loanId}/cancel")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'APPRAISER', 'APPROVER')")
    public ResponseEntity<LoanSummaryResponse> cancel(
            @PathVariable String loanId,
            @Valid @RequestBody LoanActionRequest req,
            @AuthenticationPrincipal CmsPrincipal reviewer) {
        return ResponseEntity.ok(loanService.cancel(loanId, req, reviewer));
    }

}
