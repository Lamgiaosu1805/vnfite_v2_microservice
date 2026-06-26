package com.p2plending.loan.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "repayment_auto_debit_audit")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepaymentAutoDebitAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, length = 30)
    private String triggerSource;

    @Column(length = 100)
    private String triggeredBy;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    @Column(nullable = false)
    private LocalDateTime finishedAt;

    @Column(nullable = false)
    private int scannedLoans;

    @Column(nullable = false)
    private int dueLoans;

    @Column(nullable = false)
    private int settledFull;

    @Column(nullable = false)
    private int settledPartial;

    @Column(nullable = false)
    private int noBalance;

    @Column(nullable = false)
    private int balanceError;

    @Column(nullable = false)
    private int noDue;

    @Column(nullable = false)
    private int failed;

    @Column(nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal amountCollected = BigDecimal.ZERO;

    @Column(length = 1000)
    private String errorSummary;

    @Column(nullable = false)
    @Builder.Default
    private boolean isDeleted = false;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
