package com.p2plending.loan.controller;

import com.p2plending.loan.dto.request.DisburseRequest;
import com.p2plending.loan.dto.request.InternalLoanProposeRequest;
import com.p2plending.loan.dto.request.InternalLoanReviewRequest;
import com.p2plending.loan.dto.request.LoanFilterParams;
import com.p2plending.loan.dto.request.RecordPaymentRequest;
import com.p2plending.loan.dto.response.AppraisalSuggestionResponse;
import com.p2plending.loan.dto.response.ContractResponse;
import com.p2plending.loan.dto.response.InternalLoanStatsResponse;
import com.p2plending.loan.dto.response.LoanDocumentResponse;
import com.p2plending.loan.dto.response.LoanResponse;
import com.p2plending.loan.dto.response.PagedResponse;
import com.p2plending.loan.dto.response.RepaymentScheduleResponse;
import com.p2plending.loan.service.AppraisalSuggestionService;
import com.p2plending.loan.service.ContractService;
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
    private final RepaymentService repaymentService;
    private final AppraisalSuggestionService appraisalSuggestionService;
    private final ContractService contractService;

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

    /**
     * Gợi ý hỗ trợ thẩm định: điểm rủi ro, năng lực trả nợ, số tiền & lãi suất đề xuất,
     * xem trước lịch trả nợ, và checklist thẩm định thủ công. Decision-support, không tự quyết.
     */
    @GetMapping("/{loanId}/appraisal-suggestion")
    public ResponseEntity<AppraisalSuggestionResponse> getAppraisalSuggestion(
            @RequestHeader(INTERNAL_SECRET_HEADER) String secret,
            @PathVariable String loanId,
            @RequestParam(defaultValue = "false") boolean discouraged) {
        requireInternalSecret(secret);
        return ResponseEntity.ok(appraisalSuggestionService.suggest(loanId, discouraged));
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
                request.getNote(), request.getProposedBy()));
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

    /** Danh sách chứng từ của một khoản — CMS thẩm định xem. */
    @GetMapping("/{loanId}/documents")
    public ResponseEntity<List<LoanDocumentResponse>> getDocuments(
            @RequestHeader(INTERNAL_SECRET_HEADER) String secret,
            @PathVariable String loanId) {
        requireInternalSecret(secret);
        return ResponseEntity.ok(loanService.getDocuments(loanId));
    }

    private void requireInternalSecret(String secret) {
        if (!internalSecret.equals(secret)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid internal secret");
        }
    }
}
