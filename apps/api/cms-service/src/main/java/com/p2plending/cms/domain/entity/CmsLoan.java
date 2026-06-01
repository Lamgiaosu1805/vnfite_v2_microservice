package com.p2plending.cms.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "cms_loans",
    indexes = {
        @Index(name = "idx_cl_borrower", columnList = "borrowerId"),
        @Index(name = "idx_cl_status",   columnList = "status"),
        @Index(name = "idx_cl_created",  columnList = "createdAt")
    }
)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CmsLoan {

    @Id
    private String loanId;

    @Column(length = 20)
    private String loanCode;

    @Column(nullable = false)
    private String borrowerId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    /** Nullable — set by CMS admin during approval. */
    @Column(precision = 5, scale = 2)
    private BigDecimal interestRate;

    @Column(nullable = false)
    private Integer termMonths;

    @Column(length = 500)
    private String purpose;

    @Column(length = 100)
    private String occupation;

    @Column(precision = 15, scale = 2)
    private BigDecimal monthlyIncome;

    @Column(length = 500)
    private String currentAddress;

    @Column(length = 100)
    private String referredBy;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING_REVIEW";

    @Column(length = 500)
    private String rejectionReason;

    @Column(length = 100)
    private String reviewedBy;

    private LocalDateTime reviewedAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean isDeleted = false;

    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
