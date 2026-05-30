package com.p2plending.loan.dto.response;

import com.p2plending.loan.domain.enums.OfferStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class LoanOfferResponse {
    private String id;
    private String loanRequestId;
    private String investorId;
    private BigDecimal amount;
    private OfferStatus status;
    private LocalDateTime createdAt;
}
