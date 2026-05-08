package com.p2plending.matching.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class MatchFoundEvent {
    private Long loanId;
    private Long investorId;
    private BigDecimal score;
    private BigDecimal loanAmount;
    private BigDecimal interestRate;
    private Integer termMonths;
    private LocalDateTime matchedAt;
}
