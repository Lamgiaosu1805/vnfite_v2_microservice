package com.p2plending.matching.domain.entity;

import com.p2plending.matching.domain.enums.MatchStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "match_records",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_match_loan_investor",
        columnNames = {"loanId", "investorId"}
    ),
    indexes = {
        @Index(name = "idx_match_loan",     columnList = "loanId"),
        @Index(name = "idx_match_investor", columnList = "investorId"),
        @Index(name = "idx_match_status",   columnList = "status"),
        @Index(name = "idx_match_score",    columnList = "score")
    }
)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class MatchRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String loanId;

    @Column(nullable = false)
    private String investorId;

    /** Match quality score 0.00–1.00 (higher = better fit). */
    @Column(nullable = false, precision = 4, scale = 3)
    private BigDecimal score;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private MatchStatus status = MatchStatus.PENDING;

    @Column(nullable = false)
    @Builder.Default
    private boolean isDeleted = false;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
