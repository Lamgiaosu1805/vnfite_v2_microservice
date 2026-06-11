package com.p2plending.credit.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Snapshot toàn bộ features tại thời điểm chấm điểm.
 *
 * ĐÂY LÀ DỮ LIỆU CHIẾN LƯỢC: khi khoản vay kết thúc (COMPLETED/DEFAULTED),
 * loan_outcome được điền vào → tạo thành labeled training data để sau này
 * train ML model (logistic regression / XGBoost) thay thế scorecard rule-based.
 * Không có bảng này thì không bao giờ có dữ liệu huấn luyện.
 */
@Entity
@Table(name = "feature_snapshots")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeatureSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "loan_request_id")
    private Long loanRequestId;

    @Column(name = "credit_score_id", length = 36)
    private String creditScoreId;

    /** JSON của toàn bộ features map + thông tin khoản vay */
    @Column(name = "features", nullable = false, columnDefinition = "TEXT")
    private String features;

    /** Label outcome — điền sau khi khoản vay kết thúc: COMPLETED | DEFAULTED */
    @Column(name = "loan_outcome", length = 20)
    private String loanOutcome;

    @Column(name = "outcome_at")
    private LocalDateTime outcomeAt;

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
