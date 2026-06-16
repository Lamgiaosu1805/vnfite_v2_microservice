package com.p2plending.loan.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.p2plending.loan.domain.enums.LoanStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoanPublicResponse {
    private String id;
    private String loanCode;
    private String productId;
    private String productCode;
    private String productName;
    private BigDecimal amount;
    private BigDecimal interestRate;
    private BigDecimal proposedAmount;
    private BigDecimal proposedInterestRate;
    private Integer termMonths;
    private String purpose;
    private String occupation;
    private String province;
    private LoanStatus status;
    private BigDecimal fundedAmount;
    private BigDecimal remainingAmount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
