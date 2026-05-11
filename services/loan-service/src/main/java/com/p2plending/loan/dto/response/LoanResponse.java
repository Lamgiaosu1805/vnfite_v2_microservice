package com.p2plending.loan.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.p2plending.loan.domain.enums.LoanStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoanResponse {
    private String id;
    private String borrowerId;
    private BigDecimal amount;
    private BigDecimal interestRate;
    private Integer termMonths;
    private String purpose;
    private LoanStatus status;
    private BigDecimal fundedAmount;
    private BigDecimal remainingAmount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<LoanOfferResponse> offers;
}
