package com.p2plending.cms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2plending.cms.domain.entity.LoanDecisionAuditLog;
import com.p2plending.cms.domain.repository.LoanDecisionAuditLogRepository;
import com.p2plending.cms.dto.response.AuditLogResponse;
import com.p2plending.cms.dto.response.LoanSummaryResponse;
import com.p2plending.cms.dto.response.PagedResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoanDecisionAuditLogService {

    private final LoanDecisionAuditLogRepository repository;
    private final ObjectMapper objectMapper;

    /**
     * Ghi lại bản ghi kiểm toán tại thời điểm phê duyệt / từ chối.
     *
     * @param loanBefore        Snapshot khoản vay trước khi quyết định (lấy proposed fields, requestedAmount…)
     * @param loanAfter         Kết quả sau quyết định (final amount, interestRate)
     * @param decision          "APPROVED" hoặc "REJECTED"
     * @param rejectionReason   Lý do từ chối (null nếu APPROVED)
     * @param decidedBy         Username ban lãnh đạo
     * @param deciderRole       ADMIN | SUPER_ADMIN
     * @param appraisalSnapshot JSON từ engine thẩm định (null nếu không lấy được)
     */
    @Transactional
    public void record(LoanSummaryResponse loanBefore,
                       LoanSummaryResponse loanAfter,
                       String decision,
                       String rejectionReason,
                       String decidedBy,
                       String deciderRole,
                       JsonNode appraisalSnapshot) {

        String snapshotJson = null;
        Integer creditScore = null;
        String  creditBand  = null;

        if (appraisalSnapshot != null && !appraisalSnapshot.isMissingNode()) {
            try {
                snapshotJson = objectMapper.writeValueAsString(appraisalSnapshot);
            } catch (Exception e) {
                log.warn("Cannot serialize appraisal snapshot for loan {}", loanBefore.getLoanId());
            }
            JsonNode risk = appraisalSnapshot.path("risk");
            if (!risk.isMissingNode()) {
                if (risk.hasNonNull("score")) creditScore = risk.get("score").asInt();
                if (risk.hasNonNull("band"))  creditBand  = risk.get("band").asText();
            }
        }

        LoanDecisionAuditLog entry = LoanDecisionAuditLog.builder()
                .loanId(loanBefore.getLoanId())
                .loanCode(loanBefore.getLoanCode())
                .borrowerId(loanBefore.getBorrowerId())
                .requestedAmount(loanBefore.getAmount())
                .proposedAmount(loanBefore.getProposedAmount())
                .proposedInterestRate(loanBefore.getProposedInterestRate())
                .proposedBy(loanBefore.getProposedBy())
                .finalAmount(loanAfter != null ? loanAfter.getAmount() : loanBefore.getProposedAmount())
                .finalInterestRate(loanAfter != null ? loanAfter.getInterestRate() : loanBefore.getProposedInterestRate())
                .termMonths(loanBefore.getTermMonths())
                .purpose(loanBefore.getPurpose())
                .occupation(loanBefore.getOccupation())
                .monthlyIncome(loanBefore.getMonthlyIncome())
                .creditScore(creditScore)
                .creditBand(creditBand)
                .appraisalSnapshot(snapshotJson)
                .decision(decision)
                .rejectionReason(rejectionReason)
                .decidedBy(decidedBy)
                .decidedAt(LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")))
                .deciderRole(deciderRole)
                .appraiserUsername(loanBefore.getProposedBy())
                .build();

        repository.save(entry);
        log.info("Audit log recorded: loan={} decision={} by={}", loanBefore.getLoanId(), decision, decidedBy);
    }

    @Transactional(readOnly = true)
    public PagedResponse<AuditLogResponse> list(String loanId, String decision,
                                                String decidedBy, int page, int size) {
        Page<LoanDecisionAuditLog> pg = repository.findFiltered(
                loanId, decision, decidedBy, null, null,
                PageRequest.of(page, size));

        return PagedResponse.<AuditLogResponse>builder()
                .content(pg.getContent().stream().map(e -> toResponse(e, false)).toList())
                .page(pg.getNumber())
                .size(pg.getSize())
                .totalElements(pg.getTotalElements())
                .totalPages(pg.getTotalPages())
                .last(pg.isLast())
                .build();
    }

    @Transactional(readOnly = true)
    public AuditLogResponse getById(String id) {
        LoanDecisionAuditLog entry = repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bản ghi audit: " + id));
        return toResponse(entry, true);
    }

    // ─── Mapping ─────────────────────────────────────────────────────────────────

    private AuditLogResponse toResponse(LoanDecisionAuditLog e, boolean includeSnapshot) {
        return AuditLogResponse.builder()
                .id(e.getId())
                .loanId(e.getLoanId())
                .loanCode(e.getLoanCode())
                .borrowerId(e.getBorrowerId())
                .requestedAmount(e.getRequestedAmount())
                .proposedAmount(e.getProposedAmount())
                .proposedInterestRate(e.getProposedInterestRate())
                .proposedBy(e.getProposedBy())
                .finalAmount(e.getFinalAmount())
                .finalInterestRate(e.getFinalInterestRate())
                .termMonths(e.getTermMonths())
                .purpose(e.getPurpose())
                .occupation(e.getOccupation())
                .monthlyIncome(e.getMonthlyIncome())
                .creditScore(e.getCreditScore())
                .creditBand(e.getCreditBand())
                .appraisalSnapshot(includeSnapshot ? e.getAppraisalSnapshot() : null)
                .decision(e.getDecision())
                .rejectionReason(e.getRejectionReason())
                .decidedBy(e.getDecidedBy())
                .decidedAt(e.getDecidedAt())
                .deciderRole(e.getDeciderRole())
                .appraiserUsername(e.getAppraiserUsername())
                .createdAt(e.getCreatedAt())
                .build();
    }
}
