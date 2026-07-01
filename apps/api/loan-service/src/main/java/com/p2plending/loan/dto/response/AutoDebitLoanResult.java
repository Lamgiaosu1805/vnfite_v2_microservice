package com.p2plending.loan.dto.response;

import com.p2plending.loan.domain.enums.AutoDebitLoanResultStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class AutoDebitLoanResult {
    private String loanId;
    private String loanCode;
    private String borrowerId;
    private AutoDebitLoanResultStatus status;
    private BigDecimal amountCollected;
    private String message;
}
