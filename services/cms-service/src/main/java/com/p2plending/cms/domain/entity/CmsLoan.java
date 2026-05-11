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

    @Column(nullable = false)
    private String borrowerId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal interestRate;

    @Column(nullable = false)
    private Integer termMonths;

    @Column(length = 500)
    private String purpose;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";

    @Column(nullable = false)
    @Builder.Default
    private boolean isDeleted = false;

    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
