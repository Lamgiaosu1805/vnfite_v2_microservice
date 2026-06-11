package com.p2plending.credit.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Hồ sơ tài chính tự khai của người gọi vốn — thu thập khi nộp đơn vay lần đầu.
 * Là nguồn dữ liệu chính cho scorecard ở giai đoạn cold-start (chưa có lịch sử trả nợ).
 */
@Entity
@Table(name = "borrower_profiles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BorrowerProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "user_id", nullable = false, unique = true, length = 36)
    private String userId;

    /** GOV_EMPLOYEE | SALARIED | BUSINESS_OWNER | FREELANCER | OTHER */
    @Column(name = "occupation_type", length = 30)
    private String occupationType;

    @Column(name = "employment_years", precision = 4, scale = 1)
    private BigDecimal employmentYears;

    @Column(name = "monthly_income", precision = 15, scale = 2)
    private BigDecimal monthlyIncome;

    /** SINGLE | MARRIED | DIVORCED | WIDOWED */
    @Column(name = "marital_status", length = 20)
    private String maritalStatus;

    @Column(name = "dependents_count")
    private Integer dependentsCount;

    /** POSTGRAD | UNIVERSITY | COLLEGE | HIGH_SCHOOL | OTHER */
    @Column(name = "education_level", length = 20)
    private String educationLevel;

    /** Tổng nghĩa vụ trả nợ hàng tháng hiện tại (các khoản vay khác) */
    @Column(name = "existing_monthly_debt", precision = 15, scale = 2)
    private BigDecimal existingMonthlyDebt;

    @Column(name = "notes", length = 500)
    private String notes;

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
