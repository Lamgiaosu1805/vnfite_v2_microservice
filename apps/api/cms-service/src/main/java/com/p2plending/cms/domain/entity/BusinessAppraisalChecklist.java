package com.p2plending.cms.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "business_appraisal_checklist",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_business_appraisal_loan_code",
                columnNames = {"loan_id", "checklist_code"}
        ),
        indexes = {
                @Index(name = "idx_business_appraisal_loan", columnList = "loan_id"),
                @Index(name = "idx_business_appraisal_status", columnList = "status"),
                @Index(name = "idx_business_appraisal_updated", columnList = "updated_at")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessAppraisalChecklist {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "loan_id", nullable = false, length = 36)
    private String loanId;

    @Column(name = "checklist_code", nullable = false, length = 80)
    private String checklistCode;

    @Column(nullable = false, length = 80)
    private String category;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String instruction;

    @Builder.Default
    @Column(nullable = false)
    private boolean required = false;

    @Builder.Default
    @Column(nullable = false, length = 30)
    private String status = "PENDING";

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "evidence_refs", columnDefinition = "TEXT")
    private String evidenceRefs;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

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
