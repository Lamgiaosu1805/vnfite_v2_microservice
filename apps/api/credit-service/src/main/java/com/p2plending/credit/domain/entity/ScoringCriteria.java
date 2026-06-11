package com.p2plending.credit.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Một band điểm của scorecard, cấu hình được trong DB.
 *
 * Band numeric:    min_value <= giá_trị < max_value (null = không chặn)
 * Band categorical: match_value = giá trị chuỗi (vd "MARRIED")
 *
 * Đổi trọng số không cần deploy lại code — chỉ update bảng này.
 */
@Entity
@Table(name = "scoring_criteria",
        uniqueConstraints = @UniqueConstraint(name = "uk_criteria_band", columnNames = {"criteria_code", "band_label"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoringCriteria {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /** Mã tiêu chí, trùng với key trong features map: AGE, MONTHLY_INCOME, DTI_RATIO... */
    @Column(name = "criteria_code", nullable = false, length = 50)
    private String criteriaCode;

    @Column(name = "criteria_name", nullable = false, length = 150)
    private String criteriaName;

    /** DEMOGRAPHIC | INCOME | CREDIT_HISTORY | PLATFORM | LOAN */
    @Column(name = "component", nullable = false, length = 30)
    private String component;

    @Column(name = "band_label", nullable = false, length = 100)
    private String bandLabel;

    @Column(name = "min_value", precision = 15, scale = 2)
    private BigDecimal minValue;

    @Column(name = "max_value", precision = 15, scale = 2)
    private BigDecimal maxValue;

    @Column(name = "match_value", length = 50)
    private String matchValue;

    @Column(name = "points", nullable = false)
    private Integer points;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

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
