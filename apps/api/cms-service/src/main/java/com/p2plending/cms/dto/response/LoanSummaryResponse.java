package com.p2plending.cms.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor @AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoanSummaryResponse {
    private String loanId;
    private String loanCode;
    private String borrowerId;
    private String borrowerName;
    private String borrowerPhone;
    private String productName;
    private BigDecimal amount;
    private BigDecimal interestRate;
    private BigDecimal proposedAmount;
    private BigDecimal proposedInterestRate;
    private BigDecimal appraisalFeeRate;
    private String proposedBy;
    private LocalDateTime proposedAt;
    private String appraisalNote;
    private Integer termMonths;
    private String purpose;
    private String ref1FullName;
    private String ref1Relationship;
    private String ref1Phone;
    private String ref1Address;
    private String ref2FullName;
    private String ref2Relationship;
    private String ref2Phone;
    private String ref2Address;
    private String occupation;
    private String workplace;
    private BigDecimal monthlyIncome;
    private String currentAddress;
    private String commune;
    private String province;
    private String referredBy;
    private String status;
    private String rejectionReason;
    private String reviewedBy;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
}
