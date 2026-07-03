package com.p2plending.loan.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Published by cms-service, consumed by loan-service. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanReviewedEvent {
    private String loanId;
    /** "APPROVE" or "REJECT" */
    private String action;
    /** Present when action = APPROVE */
    private BigDecimal approvedAmount;
    /** Present when action = APPROVE */
    private BigDecimal interestRate;
    /** Present when action = APPROVE */
    private Integer termMonths;
    /** Present when action = REJECT */
    private String rejectionReason;
    private String reviewedBy;
    private LocalDateTime reviewedAt;
}
