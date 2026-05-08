package com.p2plending.matching.dto.response;

import com.p2plending.matching.domain.enums.MatchStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data @Builder
public class MatchRecordResponse {
    private Long id;
    private Long loanId;
    private Long investorId;
    private BigDecimal score;
    private MatchStatus status;
    private LocalDateTime createdAt;
}
