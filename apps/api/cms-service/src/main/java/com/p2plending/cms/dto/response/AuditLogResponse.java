package com.p2plending.cms.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonRawValue;
import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor @AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuditLogResponse {

    private String id;

    // ─── Định danh ───────────────────────────────────────────────────────────────
    private String loanId;
    private String loanCode;
    private String borrowerId;

    // ─── Snapshot khoản vay ──────────────────────────────────────────────────────
    private BigDecimal requestedAmount;
    private BigDecimal proposedAmount;
    private BigDecimal proposedInterestRate;
    private String    proposedBy;
    private BigDecimal finalAmount;
    private BigDecimal finalInterestRate;
    private Integer   termMonths;
    private String    purpose;
    private String    occupation;
    private BigDecimal monthlyIncome;

    // ─── Kết quả thẩm định ───────────────────────────────────────────────────────
    private Integer creditScore;
    private String  creditBand;

    /**
     * JSON đầy đủ của AppraisalSuggestion — chỉ trả về ở endpoint detail,
     * không có trong danh sách.
     */
    @JsonRawValue
    private String appraisalSnapshot;

    // ─── Quyết định ──────────────────────────────────────────────────────────────
    /** APPROVED | REJECTED */
    private String decision;
    private String rejectionReason;

    // ─── Người ra quyết định ─────────────────────────────────────────────────────
    private String        decidedBy;
    private LocalDateTime decidedAt;
    private String        deciderRole;
    private String        appraiserUsername;

    private LocalDateTime createdAt;
}
