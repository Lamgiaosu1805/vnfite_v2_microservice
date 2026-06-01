package com.p2plending.loan.domain.enums;

public enum LoanStatus {
    PENDING_REVIEW, // submitted by borrower, awaiting CMS underwriting
    ACTIVE,         // CMS approved — live on marketplace, accepting investor offers
    FUNDED,         // fully funded by investors
    REPAYING,       // borrower making repayments
    COMPLETED,      // fully repaid
    DEFAULTED,      // borrower defaulted
    REJECTED        // rejected by CMS during underwriting
}
