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
import com.p2plending.loan.dto.response.ContractResponse;
import com.p2plending.loan.dto.response.InternalLoanStatsResponse;
import com.p2plending.loan.dto.response.LoanDocumentResponse;
import com.p2plending.loan.dto.response.LoanProductResponse;
import com.p2plending.loan.dto.response.LoanResponse;
import com.p2plending.loan.dto.response.PagedResponse;
import com.p2plending.loan.dto.response.RepaymentScheduleResponse;
import com.p2plending.loan.dto.response.RepaymentMonitoringResponse;
import com.p2plending.loan.service.AppraisalSuggestionService;
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
