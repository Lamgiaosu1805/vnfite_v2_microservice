package com.p2plending.matching.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Stores an investor's lending criteria used by the matching engine.
 * Each investor may have one active preference set at a time.
 */
@Entity
@Table(
    name = "investor_preferences",
    indexes = {
        @Index(name = "idx_pref_investor", columnList = "investorId"),
        @Index(name = "idx_pref_active",   columnList = "active")
    }
)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class InvestorPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String investorId;

    /** Minimum loan amount the investor is willing to fund. */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal minInvestmentAmount;

    /** Maximum loan amount the investor is willing to fund. */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal maxInvestmentAmount;

    /** Minimum annual interest rate (%) the investor expects. */
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal minInterestRate;

    /** Maximum annual interest rate (%) — null means no upper bound. */
    @Column(precision = 5, scale = 2)
    private BigDecimal maxInterestRate;

    /** Minimum loan term in months. */
    @Column(nullable = false)
    private Integer minTermMonths;

    /** Maximum loan term in months. */
    @Column(nullable = false)
    private Integer maxTermMonths;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(nullable = false)
    @Builder.Default
    private boolean isDeleted = false;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
