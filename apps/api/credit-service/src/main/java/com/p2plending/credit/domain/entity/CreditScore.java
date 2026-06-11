package com.p2plending.credit.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Kết quả chấm điểm tín dụng — thang 300-850, xếp hạng A-E.
 * Mỗi user chỉ có 1 score VALID tại một thời điểm; chấm lại → score cũ SUPERSEDED.
 */
@Entity
@Table(name = "credit_scores")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditScore {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    /** Khoản gọi vốn được chấm kèm (null = pre-score không gắn khoản vay) */
    @Column(name = "loan_request_id")
    private String loanRequestId;

    /** Điểm chuẩn hóa 300-850 */
    @Column(name = "score", nullable = false)
    private Integer score;

    /** A | B | C | D | E */
    @Column(name = "grade", nullable = false, length = 2)
    private String grade;

    @Column(name = "raw_points", nullable = false)
    private Integer rawPoints;

    @Column(name = "max_points", nullable = false)
    private Integer maxPoints;

    @Column(name = "model_version", nullable = false, length = 30)
    private String modelVersion;

    /** VALID | SUPERSEDED | EXPIRED */
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "VALID";

    /** Tóm tắt rủi ro do AI sinh — chỉ tư vấn, không quyết định */
    @Column(name = "ai_summary", columnDefinition = "TEXT")
    private String aiSummary;

    /** JSON array các cờ rủi ro AI phát hiện */
    @Column(name = "ai_risk_flags", columnDefinition = "TEXT")
    private String aiRiskFlags;

    @Column(name = "ai_recommendation", columnDefinition = "TEXT")
    private String aiRecommendation;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private boolean isDeleted = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
