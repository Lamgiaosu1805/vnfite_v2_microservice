package com.p2plending.cms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.p2plending.cms.domain.entity.CicManualLookup;
import com.p2plending.cms.domain.repository.CicManualLookupRepository;
import com.p2plending.cms.dto.request.CicLookupRequest;
import com.p2plending.cms.dto.request.LoanActionRequest;
import com.p2plending.cms.dto.request.LoanProposeRequest;
import com.p2plending.cms.dto.request.RecordRepaymentRequest;
import com.p2plending.cms.dto.response.AuditLogResponse;
import com.p2plending.cms.dto.response.CicLookupResponse;
import com.p2plending.cms.dto.response.LoanSummaryResponse;
import com.p2plending.cms.dto.response.PagedResponse;
import com.p2plending.cms.security.CmsPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoanManagementService {

    private final SourceServiceClient sourceServiceClient;
    private final LoanDecisionAuditLogService auditService;
    private final CicManualLookupRepository cicRepository;

    public JsonNode getLoanProducts() {
        return sourceServiceClient.getLoanProducts();
    }

    public JsonNode updateLoanProduct(String id, java.util.Map<String, Object> body) {
        return sourceServiceClient.updateLoanProduct(id, body);
    }

    public PagedResponse<LoanSummaryResponse> getLoans(String status, String borrowerId,
                                                       String province, String search, int page, int size) {
        return sourceServiceClient.getLoans(status, borrowerId, province, search, page, size);
    }

    public JsonNode getAppraisalSuggestion(String loanId, boolean discouraged, String creditGrade) {
        return sourceServiceClient.getAppraisalSuggestion(loanId, discouraged, creditGrade);
    }

    public JsonNode getRepaymentSchedule(String loanId) {
        return sourceServiceClient.getRepaymentSchedule(loanId);
    }

    public JsonNode getContracts(String loanId) {
        return sourceServiceClient.getLoanContracts(loanId);
    }

    public JsonNode getDocuments(String loanId) {
        return sourceServiceClient.getLoanDocuments(loanId);
    }

    public JsonNode evaluateCreditScore(String loanId) {
        CicManualLookup cic = cicRepository
                .findFirstByLoanIdAndIsDeletedFalseOrderByCheckedAtDescCreatedAtDesc(loanId)
                .orElse(null);
        return sourceServiceClient.evaluateCreditScore(loanId, cic);
    }

    public JsonNode getLatestCreditScore(String loanId) {
        return sourceServiceClient.getLatestCreditScore(loanId);
    }

    public JsonNode analyzeDocument(String loanId, String documentId) {
        return sourceServiceClient.analyzeLoanDocument(loanId, documentId);
    }

    // ─── CIC nhập tay (chờ API CIC sandbox NĐ94) ────────────────────────────────────

    /** Lấy kết quả tra CIC mới nhất của khoản (null nếu chưa nhập). */
    @Transactional(readOnly = true)
    public CicLookupResponse getCicLookup(String loanId) {
        return cicRepository.findFirstByLoanIdAndIsDeletedFalseOrderByCheckedAtDescCreatedAtDesc(loanId)
                .map(CicLookupResponse::from)
                .orElse(null);
    }

    /** Thẩm định viên nhập kết quả tra CIC ngoài → lưu kèm audit (ai/khi nào). */
    @Transactional
    public CicLookupResponse saveCicLookup(String loanId, CicLookupRequest req, CmsPrincipal operator) {
        String borrowerId = null;
        try {
            borrowerId = safeGetLoan(loanId).getBorrowerId();
        } catch (Exception e) {
            log.warn("Could not resolve borrowerId for CIC lookup of loan {}: {}", loanId, e.getMessage());
        }

        CicManualLookup entity = CicManualLookup.builder()
                .loanId(loanId)
                .borrowerId(borrowerId)
                .debtGroup(req.getDebtGroup())
                .maxDpd(req.getMaxDpd())
                .activeLenders(req.getActiveLenders())
                .totalOutstanding(req.getTotalOutstanding())
                .inquiriesRecent(req.getInquiriesRecent())
                .checkedAt(req.getCheckedAt())
                .attachmentFileId(req.getAttachmentFileId())
                .note(req.getNote())
                .consentConfirmed(req.isConsentConfirmed())
                .enteredBy(operator != null ? operator.displayName() : "unknown")
                .build();
        entity = cicRepository.save(entity);
        log.info("CIC lookup saved: loanId={} debtGroup={} checkedAt={} by={}",
                loanId, entity.getDebtGroup(), entity.getCheckedAt(), entity.getEnteredBy());
        return CicLookupResponse.from(entity);
    }

    /**
     * Giải ngân vốn cho người gọi vốn (OPS): AWAITING_DISBURSEMENT → DISBURSED.
     * Ghi audit log quyết định giải ngân.
     */
    /** Chạy ngay job hết hạn gọi vốn / ký khế ước (vận hành/test). Trả về số khoản đã xử lý. */
    public JsonNode expireSweep() {
        return sourceServiceClient.expireSweep();
    }

    /** Chạy ngay job thu nợ tự động từ ví người gọi vốn. */
    public JsonNode autoDebitSweep(CmsPrincipal operator) {
        String triggeredBy = operator != null ? operator.displayName() : "unknown";
        return sourceServiceClient.autoDebitSweep(triggeredBy);
    }

    /** Lịch sử quét auto-debit — passthrough JSON từ loan-service. */
    public JsonNode getAutoDebitAudit(int limit) {
        return sourceServiceClient.getAutoDebitAudit(limit);
    }

    /** Chi tiết từng khoản trong một lần quét auto-debit. */
    public JsonNode getAutoDebitAuditItems(String auditId) {
        return sourceServiceClient.getAutoDebitAuditItems(auditId);
    }

    /** Log phân bổ nhà đầu tư (thuế TNCN) — passthrough JSON từ loan-service. */
    public JsonNode getDistributionLog(String loanId, String investorId, int page, int size) {
        return sourceServiceClient.getDistributionLog(loanId, investorId, page, size);
    }

    /** Sổ tất toán trước hạn — passthrough JSON từ loan-service. */
    public JsonNode getEarlySettlements(int page, int size) {
        return sourceServiceClient.getEarlySettlements(page, size);
    }

    /** Báo giá tất toán trước hạn của 1 khoản (chỉ xem) — passthrough JSON từ loan-service. */
    public JsonNode getEarlySettlementQuote(String loanId) {
        return sourceServiceClient.getEarlySettlementQuote(loanId);
    }

    /** Sổ cái doanh thu phí — passthrough JSON từ loan-service. */
    public JsonNode getFeeRevenueReport(int page, int size) {
        return sourceServiceClient.getFeeRevenueReport(page, size);
    }

    /** Kỳ trả nợ đến hạn theo ngày, enrich thông tin người gọi vốn. */
    public JsonNode getDueTodaySchedules(String date) {
        return sourceServiceClient.getDueTodaySchedules(date);
    }

    public LoanSummaryResponse disburse(String loanId, CmsPrincipal operator) {
        LoanSummaryResponse loanBefore = safeGetLoan(loanId);
        String decidedBy   = operator != null ? operator.displayName() : "unknown";
        String deciderRole = operator != null ? operator.role() : null;

        LoanSummaryResponse result = sourceServiceClient.disburseLoan(loanId, decidedBy);

        try {
            auditService.record(loanBefore, result, "DISBURSED", null, decidedBy, deciderRole, null);
        } catch (Exception e) {
            log.error("Failed to record audit log for disbursement of loan {}: {}", loanId, e.getMessage());
        }
        return result;
    }

    /**
     * Admin ghi nhận một lần trả nợ thủ công khi khách trả tiền mặt / chuyển khoản ngoài ví VNFITE.
     * Tiền áp vào kỳ sớm nhất chưa trả ở loan-service. Trả về lịch trả nợ mới + ghi audit trail.
     */
    public JsonNode recordRepayment(String loanId, RecordRepaymentRequest req, CmsPrincipal operator) {
        LoanSummaryResponse loanBefore = safeGetLoan(loanId);
        String recordedBy  = operator != null ? operator.displayName() : "unknown";
        String deciderRole = operator != null ? operator.role() : null;

        JsonNode schedule = sourceServiceClient.recordRepayment(
                loanId, req.getAmount(), req.getReason(),
                "MANUAL_ADMIN", recordedBy, req.getExternalRef());

        try {
            LoanSummaryResponse loanAfter = safeGetLoan(loanId);
            auditService.record(loanBefore, loanAfter, "REPAYMENT_RECORDED",
                    req.getReason(), recordedBy, deciderRole, null);
        } catch (Exception e) {
            log.error("Failed to record audit log for manual repayment of loan {}: {}", loanId, e.getMessage());
        }
        return schedule;
    }

    public LoanSummaryResponse propose(String loanId, LoanProposeRequest req, CmsPrincipal proposer) {
        // Guard: kết quả CIC phải được nhập trước khi thẩm định viên trình ban lãnh đạo.
        cicRepository.findFirstByLoanIdAndIsDeletedFalseOrderByCheckedAtDescCreatedAtDesc(loanId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Cần nhập kết quả tra CIC trước khi trình ban lãnh đạo."));

        return sourceServiceClient.proposeLoan(loanId, req.getProposedAmount(), req.getProposedInterestRate(),
                req.getAppraisalFeeRate(), req.getNote(), proposer != null ? proposer.displayName() : null);
    }

    /**
     * Ban lãnh đạo phê duyệt khoản gọi vốn.
     * Chụp snapshot khoản vay + engine thẩm định trước khi ghi quyết định.
     */
    public LoanSummaryResponse approve(String loanId, LoanActionRequest req, CmsPrincipal reviewer) {
        // 1. Snapshot trạng thái trước quyết định
        LoanSummaryResponse loanBefore = safeGetLoan(loanId);

        // 2. Engine thẩm định tại thời điểm duyệt (non-fatal)
        JsonNode appraisal = safeGetAppraisal(loanId);

        // 3. Gửi quyết định sang loan-service
        String decidedBy  = reviewer != null ? reviewer.displayName() : "unknown";
        String deciderRole = reviewer != null ? reviewer.role() : null;
        LoanSummaryResponse result = sourceServiceClient.approveLoan(loanId, req, decidedBy);

        // 4. Ghi audit log (bất đồng bộ không — vẫn trong request thread, lỗi log không ném ra ngoài)
        try {
            auditService.record(loanBefore, result, "APPROVED", null, decidedBy, deciderRole, appraisal);
        } catch (Exception e) {
            log.error("Failed to record audit log for approval of loan {}: {}", loanId, e.getMessage());
        }

        return result;
    }

    /**
     * Ban lãnh đạo từ chối khoản gọi vốn.
     * Chụp snapshot khoản vay + engine thẩm định trước khi ghi quyết định.
     */
    public LoanSummaryResponse reject(String loanId, LoanActionRequest req, CmsPrincipal reviewer) {
        // 1. Snapshot trạng thái trước quyết định
        LoanSummaryResponse loanBefore = safeGetLoan(loanId);

        // 2. Engine thẩm định (non-fatal)
        JsonNode appraisal = safeGetAppraisal(loanId);

        // 3. Gửi quyết định sang loan-service
        String decidedBy   = reviewer != null ? reviewer.displayName() : "unknown";
        String deciderRole = reviewer != null ? reviewer.role() : null;
        LoanSummaryResponse result = sourceServiceClient.rejectLoan(loanId, req, decidedBy);

        // 4. Ghi audit log
        try {
            auditService.record(loanBefore, result, "REJECTED", req.getReason(), decidedBy, deciderRole, appraisal);
        } catch (Exception e) {
            log.error("Failed to record audit log for rejection of loan {}: {}", loanId, e.getMessage());
        }

        return result;
    }

    /**
     * CMS hủy khoản gọi vốn trước khi giải ngân. loan-service sẽ hoàn tiền/void hợp đồng nếu đã có nhà đầu tư.
     */
    public LoanSummaryResponse cancel(String loanId, LoanActionRequest req, CmsPrincipal reviewer) {
        LoanSummaryResponse loanBefore = safeGetLoan(loanId);
        String decidedBy = reviewer != null ? reviewer.displayName() : "unknown";
        String deciderRole = reviewer != null ? reviewer.role() : null;

        LoanSummaryResponse result = sourceServiceClient.cancelLoan(loanId, req, decidedBy);

        try {
            auditService.record(loanBefore, result, "CANCELLED", req.getReason(), decidedBy, deciderRole, null);
        } catch (Exception e) {
            log.error("Failed to record audit log for cancellation of loan {}: {}", loanId, e.getMessage());
        }

        return result;
    }

    // ─── Audit log queries ────────────────────────────────────────────────────────

    public PagedResponse<AuditLogResponse> listAuditLogs(String loanId, String decision,
                                                         String decidedBy, int page, int size) {
        return auditService.list(loanId, decision, decidedBy, page, size);
    }

    public AuditLogResponse getAuditLogById(String id) {
        return auditService.getById(id);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    private LoanSummaryResponse safeGetLoan(String loanId) {
        try {
            return sourceServiceClient.getLoanById(loanId);
        } catch (Exception e) {
            log.warn("Could not fetch loan {} for audit snapshot: {}", loanId, e.getMessage());
            // Trả về object rỗng có loanId để audit không mất hoàn toàn
            return LoanSummaryResponse.builder().loanId(loanId).build();
        }
    }

    private JsonNode safeGetAppraisal(String loanId) {
        try {
            return sourceServiceClient.getAppraisalSuggestion(loanId, false, null);
        } catch (Exception e) {
            log.warn("Could not fetch appraisal for audit snapshot of loan {}: {}", loanId, e.getMessage());
            return null;
        }
    }

}
