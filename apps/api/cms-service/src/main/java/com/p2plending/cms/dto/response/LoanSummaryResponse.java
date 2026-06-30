package com.p2plending.cms.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

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
    private String borrowerEmail;
    private String borrowerCccdNumber;
    private String borrowerKycStatus;
    private String borrowerAccountStatus;
    private java.time.LocalDate borrowerDateOfBirth;
    private String borrowerGender;
    private String borrowerPermanentAddress;
    private String borrowerHometown;
    private java.time.LocalDate borrowerIssueDate;
    private String borrowerIssuingAuthority;
    private java.time.LocalDate borrowerExpiryDate;
    private String borrowerFrontImageId;
    private String borrowerBackImageId;
    private String borrowerPortraitImageId;
    private String productName;
    private BigDecimal amount;
    /** Tổng tiền nhà đầu tư đã cam kết (offer ACCEPTED) — tiến độ gọi vốn của khoản ACTIVE. */
    private BigDecimal fundedAmount;
    private BigDecimal interestRate;
    private BigDecimal proposedAmount;
    private BigDecimal proposedInterestRate;
    private BigDecimal appraisalFeeRate;
    private BigDecimal appraisalFee;
    private BigDecimal vatAmount;
    private BigDecimal totalFee;
    private BigDecimal netDisbursement;
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
    private List<LoanOfferSummaryResponse> offers;
}
