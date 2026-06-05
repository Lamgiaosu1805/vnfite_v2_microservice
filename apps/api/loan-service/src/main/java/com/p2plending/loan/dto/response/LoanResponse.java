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
    private String loanCode;
    private String productId;
    private String productCode;
    private String productName;
    private String borrowerId;
    private BigDecimal amount;
    private BigDecimal interestRate;
    private BigDecimal proposedAmount;
    private BigDecimal proposedInterestRate;
    private String proposedBy;
    private LocalDateTime proposedAt;
    private String appraisalNote;
    private Integer termMonths;
    private String purpose;
    private String referredBy;
    private String ref1FullName;
    private String ref1Relationship;
    private String ref1Phone;
    private String ref1Address;
    private String ref2FullName;
    private String ref2Relationship;
    private String ref2Phone;
    private String ref2Address;
    private BigDecimal monthlyIncome;
    private String occupation;
    private String workplace;
    private String currentAddress;
    private String commune;
    private String province;
    private LoanStatus status;
    private BigDecimal fundedAmount;
    private BigDecimal remainingAmount;
    private String rejectionReason;
    private String borrowerCancelledReason;
    private LocalDateTime reviewedAt;
    private String reviewedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<LoanOfferResponse> offers;
}
