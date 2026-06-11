package com.p2plending.credit.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Điểm chi tiết từng tiêu chí của một lần chấm — để CMS hiển thị breakdown
 * và giải thích được vì sao ra điểm đó (explainability).
 */
@Entity
@Table(name = "credit_score_details")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditScoreDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "credit_score_id", nullable = false, length = 36)
    private String creditScoreId;

    @Column(name = "criteria_code", nullable = false, length = 50)
    private String criteriaCode;

    @Column(name = "criteria_name", length = 150)
    private String criteriaName;

    @Column(name = "component", nullable = false, length = 30)
    private String component;

    /** Giá trị thực tế của user tại thời điểm chấm (vd "27", "MARRIED", "(thiếu dữ liệu)") */
    @Column(name = "raw_value", length = 255)
    private String rawValue;

    @Column(name = "points", nullable = false)
    private Integer points;

    @Column(name = "max_points", nullable = false)
    private Integer maxPoints;

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
