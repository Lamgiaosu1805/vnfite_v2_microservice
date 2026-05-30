package com.p2plending.cms.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "daily_stats",
    indexes = @Index(name = "idx_ds_date", columnList = "statDate", unique = true))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class DailyStat {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, unique = true)
    private LocalDate statDate;

    @Builder.Default private long newUsers    = 0;
    @Builder.Default private long newLoans    = 0;
    @Builder.Default private long fundedLoans = 0;

    @Column(precision = 18, scale = 2)
    @Builder.Default private BigDecimal loanVolume = BigDecimal.ZERO;

    @Column(nullable = false)
    @Builder.Default
    private boolean isDeleted = false;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
