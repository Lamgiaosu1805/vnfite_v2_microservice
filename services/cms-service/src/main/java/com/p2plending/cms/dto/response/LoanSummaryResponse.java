package com.p2plending.cms.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data @Builder
public class LoanSummaryResponse {
    private String loanId;
    private String borrowerId;
    private BigDecimal amount;
    private BigDecimal interestRate;
    private Integer termMonths;
    private String purpose;
    private String status;
    private LocalDateTime createdAt;
}
