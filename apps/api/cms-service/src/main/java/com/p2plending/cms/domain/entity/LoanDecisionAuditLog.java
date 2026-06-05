package com.p2plending.cms.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Bản ghi kiểm toán quyết định phê duyệt / từ chối khoản gọi vốn.
 *
 * Chụp lại toàn bộ dữ liệu tại thời điểm ban lãnh đạo ra quyết định:
 *   - Thông tin khoản vay (số tiền yêu cầu, đề xuất, cuối cùng, lãi suất, kỳ hạn)
 *   - Kết quả engine thẩm định (điểm tín dụng, hạng, JSON đầy đủ)
 *   - Quyết định (APPROVED / REJECTED), lý do từ chối
 *   - Thẩm định viên đề xuất (cấp 1) và ban lãnh đạo phê duyệt (cấp 2)
 *
 * Dữ liệu này phục vụ audit trail tuân thủ và là nguồn nhãn cho ML Phase 1.
 */
@Entity
@Table(
    name = "loan_decision_audit_log",
    indexes = {
        @Index(name = "idx_audit_loan_id",    columnList = "loan_id"),
        @Index(name = "idx_audit_decided_at", columnList = "decided_at"),
        @Index(name = "idx_audit_decision",   columnList = "decision"),
        @Index(name = "idx_audit_decided_by", columnList = "decided_by")
    }
)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class LoanDecisionAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "loan_id", nullable = false, length = 36)
    private String loanId;

    @Column(name = "loan_code", length = 50)
    private String loanCode;

    @Column(name = "borrower_id", length = 36)
    private String borrowerId;

    // ─── Snapshot khoản vay ──────────────────────────────────────────────────────

    @Column(name = "requested_amount", precision = 15, scale = 2)
    private BigDecimal requestedAmount;

    @Column(name = "proposed_amount", precision = 15, scale = 2)
    private BigDecimal proposedAmount;

    @Column(name = "proposed_interest_rate", precision = 5, scale = 2)
    private BigDecimal proposedInterestRate;

    @Column(name = "proposed_by", length = 100)
    private String proposedBy;

    @Column(name = "final_amount", precision = 15, scale = 2)
    private BigDecimal finalAmount;

    @Column(name = "final_interest_rate", precision = 5, scale = 2)
    private BigDecimal finalInterestRate;

    @Column(name = "term_months")
    private Integer termMonths;

    @Column(name = "purpose", length = 500)
    private String purpose;

    @Column(name = "occupation", length = 100)
    private String occupation;

    @Column(name = "monthly_income", precision = 15, scale = 2)
    private BigDecimal monthlyIncome;

    // ─── Kết quả engine thẩm định ────────────────────────────────────────────────

    @Column(name = "credit_score")
    private Integer creditScore;

    @Column(name = "credit_band", length = 5)
    private String creditBand;

    /** JSON đầy đủ của AppraisalSuggestion — features cho ML Phase 1. */
    @Column(name = "appraisal_snapshot", columnDefinition = "JSON")
    private String appraisalSnapshot;

    // ─── Quyết định ──────────────────────────────────────────────────────────────

    /** APPROVED hoặc REJECTED */
    @Column(name = "decision", nullable = false, length = 20)
    private String decision;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    /** Ban lãnh đạo ra quyết định (cấp 2). */
    @Column(name = "decided_by", nullable = false, length = 100)
    private String decidedBy;

    @Column(name = "decided_at", nullable = false)
    private LocalDateTime decidedAt;

    @Column(name = "decider_role", length = 20)
    private String deciderRole;

    /** Thẩm định viên đề xuất (cấp 1) — để đánh giá chất lượng thẩm định về sau. */
    @Column(name = "appraiser_username", length = 100)
    private String appraiserUsername;

    // ─── Audit fields chuẩn ──────────────────────────────────────────────────────

    @Builder.Default
    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
