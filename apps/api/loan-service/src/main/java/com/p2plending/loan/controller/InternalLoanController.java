package com.p2plending.loan.controller;

import com.p2plending.loan.domain.enums.LoanStatus;
import com.p2plending.loan.domain.repository.LoanRequestRepository;
import com.p2plending.loan.dto.request.DisburseRequest;
import com.p2plending.loan.dto.request.InternalLoanProposeRequest;
import com.p2plending.loan.dto.request.InternalLoanReviewRequest;
import com.p2plending.loan.dto.request.LoanFilterParams;
import com.p2plending.loan.dto.request.LoanProductUpdateRequest;
import com.p2plending.loan.dto.request.RecordPaymentRequest;
import com.p2plending.loan.dto.response.AppraisalSuggestionResponse;
import com.p2plending.loan.dto.response.AutoDebitSweepResponse;
import com.p2plending.loan.dto.response.CashflowResponse;
import com.p2plending.loan.dto.response.ContractResponse;
import com.p2plending.loan.dto.response.InternalLoanStatsResponse;
import com.p2plending.loan.dto.response.LoanDocumentResponse;
import com.p2plending.loan.dto.response.LoanProductResponse;
import com.p2plending.loan.dto.response.LoanResponse;
import com.p2plending.loan.dto.response.PagedResponse;
import com.p2plending.loan.dto.response.RepaymentScheduleResponse;
import com.p2plending.loan.dto.response.RepaymentMonitoringResponse;
import com.p2plending.loan.service.AppraisalSuggestionService;
import com.p2plending.loan.service.AutoDebitSweepService;
import com.p2plending.loan.service.CashflowService;
import com.p2plending.loan.service.ContractService;
import com.p2plending.loan.service.FundingExpiryService;
import com.p2plending.loan.service.LoanProductService;
import com.p2plending.loan.service.LoanService;
import com.p2plending.loan.service.RepaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/internal/loans")
@RequiredArgsConstructor
public class InternalLoanController {

    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";

    private final LoanService loanService;
    private final LoanProductService loanProductService;
    private final RepaymentService repaymentService;
    private final CashflowService cashflowService;
    private final AutoDebitSweepService autoDebitSweepService;
    private final AppraisalSuggestionService appraisalSuggestionService;
    private final ContractService contractService;
    private final LoanRequestRepository loanRequestRepository;
    private final FundingExpiryService fundingExpiryService;

    @Value("${app.internal.secret}")
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

    /** Sổ tất toán trước hạn — CMS màn "Tất toán sớm", phân trang, mới nhất trước. */
    @GetMapping("/early-settlements")
    public ResponseEntity<PagedResponse<com.p2plending.loan.domain.entity.EarlySettlement>> listEarlySettlements(
            @RequestHeader(INTERNAL_SECRET_HEADER) String secret,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireInternalSecret(secret);
        return ResponseEntity.ok(PagedResponse.from(repaymentService.listEarlySettlements(page, size)));
    }

    /**
     * GET /internal/loans/{loanId}/early-settlement/quote
     * CMS xem trước báo giá tất toán sớm (không trừ tiền, không đổi trạng thái khoản) —
     * dùng để admin/ops soi số trước khi công bố hoặc tư vấn cho người gọi vốn.
     */
    @GetMapping("/{loanId}/early-settlement/quote")
    public ResponseEntity<com.p2plending.loan.dto.response.EarlySettlementQuoteResponse> getEarlySettlementQuote(
            @RequestHeader(INTERNAL_SECRET_HEADER) String secret,
            @PathVariable String loanId) {
        requireInternalSecret(secret);
        return ResponseEntity.ok(repaymentService.quoteEarlySettlement(loanId));
    }

    /** Sổ cái doanh thu phí — tổng + danh sách phân trang (CMS màn "Doanh thu phí"). */
    @GetMapping("/stats/fee-revenue")
    public ResponseEntity<com.p2plending.loan.dto.response.FeeRevenueReportResponse> getFeeRevenue(
            @RequestHeader(INTERNAL_SECRET_HEADER) String secret,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        requireInternalSecret(secret);
        return ResponseEntity.ok(loanService.getFeeRevenueReport(page, size));
    }

    /** Tổng hợp dư nợ, kỳ sắp đến hạn và quá hạn cho Dashboard CMS. */
    @GetMapping("/repayment-monitoring")
    public ResponseEntity<RepaymentMonitoringResponse> getRepaymentMonitoring(
            @RequestHeader(INTERNAL_SECRET_HEADER) String secret,
            @RequestParam(defaultValue = "7") int dueWithinDays) {
        requireInternalSecret(secret);
        return ResponseEntity.ok(repaymentService.getMonitoring(dueWithinDays));
    }

    /**
     * Gợi ý hỗ trợ thẩm định: điểm rủi ro, năng lực trả nợ, số tiền & lãi suất đề xuất,
     * xem trước lịch trả nợ, và checklist thẩm định thủ công. Decision-support, không tự quyết.
     */
    @GetMapping("/{loanId}/appraisal-suggestion")
    public ResponseEntity<AppraisalSuggestionResponse> getAppraisalSuggestion(
            @RequestHeader(INTERNAL_SECRET_HEADER) String secret,
            @PathVariable String loanId,
            @RequestParam(defaultValue = "false") boolean discouraged,
            @RequestParam(required = false) String creditGrade) {
        requireInternalSecret(secret);
        return ResponseEntity.ok(appraisalSuggestionService.suggest(loanId, discouraged, creditGrade));
    }

    /** Cấp 1 — thẩm định viên đề xuất số tiền + lãi suất trình ban lãnh đạo. */
    @PutMapping("/{loanId}/propose")
    public ResponseEntity<LoanResponse> proposeLoan(
            @RequestHeader(INTERNAL_SECRET_HEADER) String secret,
            @PathVariable String loanId,
            @Valid @RequestBody InternalLoanProposeRequest request) {
        requireInternalSecret(secret);
        return ResponseEntity.ok(loanService.proposeLoan(loanId,
                request.getProposedAmount(), request.getProposedInterestRate(),
                request.getAppraisalFeeRate(), request.getNote(), request.getProposedBy()));
    }

    /** Cấp 2 — ban lãnh đạo duyệt (có thể sửa lãi suất trước khi duyệt). */
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

    // ─── Repayment schedule & DPD ───────────────────────────────────────────────

    /** Lịch trả nợ của một khoản (CMS hiển thị + theo dõi DPD). */
    @GetMapping("/{loanId}/repayments")
    public ResponseEntity<List<RepaymentScheduleResponse>> getRepaymentSchedule(
            @RequestHeader(INTERNAL_SECRET_HEADER) String secret,
            @PathVariable String loanId) {
        requireInternalSecret(secret);
        return ResponseEntity.ok(repaymentService.getSchedule(loanId));
    }

    /** Ghi nhận một lần trả nợ (admin nhập tay hoặc webhook đối tác thu hộ). */
    @PostMapping("/{loanId}/repayments")
    public ResponseEntity<List<RepaymentScheduleResponse>> recordPayment(
            @RequestHeader(INTERNAL_SECRET_HEADER) String secret,
            @PathVariable String loanId,
            @Valid @RequestBody RecordPaymentRequest request) {
        requireInternalSecret(secret);
        return ResponseEntity.ok(repaymentService.recordPayment(loanId, request));
    }

    /**
     * Chạy ngay job thu nợ tự động từ ví người gọi vốn (CMS bấm tay).
     * Dùng khi khách vừa nạp tiền sau lượt cron cuối trong ngày.
     */
    @PostMapping("/repayments/auto-debit-sweep")
    public ResponseEntity<AutoDebitSweepResponse> autoDebitSweep(
            @RequestHeader(INTERNAL_SECRET_HEADER) String secret,
            @RequestParam(required = false) String triggeredBy) {
        requireInternalSecret(secret);
        return ResponseEntity.ok(autoDebitSweepService.runSweep("MANUAL_CMS",
                triggeredBy != null && !triggeredBy.isBlank() ? triggeredBy : "cms"));
    }

    /** Lịch sử quét auto-debit — CMS hiển thị. */
    @GetMapping("/repayments/auto-debit-audit")
    public ResponseEntity<java.util.List<com.p2plending.loan.dto.response.AutoDebitSweepResponse>> getAutoDebitAudit(
            @RequestHeader(INTERNAL_SECRET_HEADER) String secret,
            @RequestParam(defaultValue = "200") int limit) {
        requireInternalSecret(secret);
        return ResponseEntity.ok(repaymentService.getAutoDebitAuditList(limit));
    }

    /** Chi tiết từng khoản trong một lần quét auto-debit — CMS hiển thị khi bấm vào lịch sử. */
    @GetMapping("/repayments/auto-debit-audit/{auditId}/items")
    public ResponseEntity<java.util.List<com.p2plending.loan.domain.entity.RepaymentAutoDebitAuditItem>> getAutoDebitAuditItems(
            @RequestHeader(INTERNAL_SECRET_HEADER) String secret,
            @PathVariable String auditId) {
        requireInternalSecret(secret);
        return ResponseEntity.ok(repaymentService.getAutoDebitAuditItems(auditId));
    }

    /** Danh sách kỳ trả nợ đến hạn theo ngày — CMS theo dõi ai đã/chưa trả. */
    @GetMapping("/repayments/due-today")
    public ResponseEntity<List<com.p2plending.loan.dto.response.DueTodayItem>> getDueTodayList(
            @RequestHeader(INTERNAL_SECRET_HEADER) String secret,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        requireInternalSecret(secret);
        return ResponseEntity.ok(repaymentService.getDueTodayList(date));
    }

    /** Log phân bổ tiền cho nhà đầu tư (gốc/lãi/thuế/net) — CMS kế toán tra soát. */
    @GetMapping("/repayments/distribution-log")
    public ResponseEntity<org.springframework.data.domain.Page<com.p2plending.loan.domain.entity.InvestorDistributionLog>> getDistributionLog(
            @RequestHeader(INTERNAL_SECRET_HEADER) String secret,
            @RequestParam(required = false) String loanId,
            @RequestParam(required = false) String investorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        requireInternalSecret(secret);
        return ResponseEntity.ok(repaymentService.getDistributionLog(loanId, investorId, page, size));
    }

    // ─── Hợp đồng & giải ngân ───────────────────────────────────────────────────

    /** Danh sách hợp đồng (vay + đầu tư) của một khoản — CMS hiển thị. */
    @GetMapping("/{loanId}/contracts")
    public ResponseEntity<List<ContractResponse>> getContracts(
            @RequestHeader(INTERNAL_SECRET_HEADER) String secret,
            @PathVariable String loanId) {
        requireInternalSecret(secret);
        return ResponseEntity.ok(contractService.getContractsByLoan(loanId));
    }

    /** Giải ngân vốn cho người gọi vốn (OPS bấm trên CMS): AWAITING_DISBURSEMENT → DISBURSED. */
    @PostMapping("/{loanId}/disburse")
    public ResponseEntity<LoanResponse> disburse(
            @RequestHeader(INTERNAL_SECRET_HEADER) String secret,
            @PathVariable String loanId,
            @RequestBody(required = false) DisburseRequest request) {
        requireInternalSecret(secret);
        String disbursedBy = request != null ? request.getDisbursedBy() : null;
        return ResponseEntity.ok(loanService.disburse(loanId, disbursedBy));
    }

    /** Số khoản gọi vốn đã COMPLETED của borrower — credit-service dùng cho scoring. */
    @GetMapping("/borrowers/{borrowerId}/completed-count")
    public ResponseEntity<Long> getCompletedLoanCount(
            @RequestHeader(INTERNAL_SECRET_HEADER) String secret,
            @PathVariable String borrowerId) {
        requireInternalSecret(secret);
        return ResponseEntity.ok(
                loanRequestRepository.countByBorrowerIdAndStatusAndIsDeletedFalse(borrowerId, LoanStatus.COMPLETED));
    }

    /** Danh mục đầu tư và dòng tiền của một nhà đầu tư — CMS chi tiết khách hàng dùng. */
    @GetMapping("/investors/{investorId}/cashflow")
    public ResponseEntity<CashflowResponse> getInvestorCashflow(
            @RequestHeader(INTERNAL_SECRET_HEADER) String secret,
            @PathVariable String investorId) {
        requireInternalSecret(secret);
        return ResponseEntity.ok(cashflowService.getCashflow(investorId));
    }

    /** Danh sách chứng từ của một khoản — CMS thẩm định xem. */
    @GetMapping("/{loanId}/documents")
    public ResponseEntity<List<LoanDocumentResponse>> getDocuments(
            @RequestHeader(INTERNAL_SECRET_HEADER) String secret,
            @PathVariable String loanId) {
        requireInternalSecret(secret);
        return ResponseEntity.ok(loanService.getDocuments(loanId));
    }

    /**
     * Chạy ngay job hết hạn gọi vốn / ký khế ược (thay vì chờ cron 01:30). CMS gọi qua nút vận hành.
     * Trả về số khoản đã xử lý từng loại.
     */
    @PostMapping("/expire-sweep")
    public ResponseEntity<FundingExpiryService.ExpirySweepResult> expireSweep(
            @RequestHeader(INTERNAL_SECRET_HEADER) String secret) {
        requireInternalSecret(secret);
        return ResponseEntity.ok(fundingExpiryService.runSweep());
    }

    @PutMapping("/products/{id}")
    public ResponseEntity<LoanProductResponse> updateProduct(
            @RequestHeader(INTERNAL_SECRET_HEADER) String secret,
            @PathVariable String id,
            @Valid @RequestBody LoanProductUpdateRequest request) {
        requireInternalSecret(secret);
        return ResponseEntity.ok(loanProductService.updateProduct(id, request));
    }

    private void requireInternalSecret(String secret) {
        if (!internalSecret.equals(secret)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid internal secret");
        }
    }
}
