package com.p2plending.cms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.p2plending.cms.dto.request.LoanActionRequest;
import com.p2plending.cms.dto.request.LoanProposeRequest;
import com.p2plending.cms.dto.response.AuditLogResponse;
import com.p2plending.cms.dto.response.LoanSummaryResponse;
import com.p2plending.cms.dto.response.PagedResponse;
import com.p2plending.cms.security.CmsPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoanManagementService {

    private final SourceServiceClient sourceServiceClient;
    private final LoanDecisionAuditLogService auditService;

    public PagedResponse<LoanSummaryResponse> getLoans(String status, String borrowerId,
                                                       String province, String search, int page, int size) {
        return sourceServiceClient.getLoans(status, borrowerId, province, search, page, size);
    }

    public JsonNode getAppraisalSuggestion(String loanId, boolean discouraged) {
        return sourceServiceClient.getAppraisalSuggestion(loanId, discouraged);
    }

    public JsonNode getRepaymentSchedule(String loanId) {
        return sourceServiceClient.getRepaymentSchedule(loanId);
    }

    public JsonNode getContracts(String loanId) {
        return sourceServiceClient.getLoanContracts(loanId);
    }

    /**
     * Giải ngân vốn cho người gọi vốn (OPS): AWAITING_DISBURSEMENT → DISBURSED.
     * Ghi audit log quyết định giải ngân.
     */
    public LoanSummaryResponse disburse(String loanId, CmsPrincipal operator) {
        LoanSummaryResponse loanBefore = safeGetLoan(loanId);
        String decidedBy   = operator != null ? operator.username() : "unknown";
        String deciderRole = operator != null ? operator.role() : null;

        LoanSummaryResponse result = sourceServiceClient.disburseLoan(loanId, decidedBy);

        try {
            auditService.record(loanBefore, result, "DISBURSED", null, decidedBy, deciderRole, null);
        } catch (Exception e) {
            log.error("Failed to record audit log for disbursement of loan {}: {}", loanId, e.getMessage());
        }
        return result;
    }

    public LoanSummaryResponse propose(String loanId, LoanProposeRequest req, CmsPrincipal proposer) {
        return sourceServiceClient.proposeLoan(loanId, req.getProposedAmount(), req.getProposedInterestRate(),
                req.getNote(), proposer != null ? proposer.username() : null);
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
        String decidedBy  = reviewer != null ? reviewer.username() : "unknown";
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
        String decidedBy   = reviewer != null ? reviewer.username() : "unknown";
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
            return sourceServiceClient.getAppraisalSuggestion(loanId, false);
        } catch (Exception e) {
            log.warn("Could not fetch appraisal for audit snapshot of loan {}: {}", loanId, e.getMessage());
            return null;
        }
    }
}
