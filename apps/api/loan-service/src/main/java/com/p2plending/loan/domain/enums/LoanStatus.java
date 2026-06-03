package com.p2plending.loan.domain.enums;

public enum LoanStatus {
    PENDING_REVIEW,             // submitted by borrower, awaiting CMS underwriting
    AWAITING_BORROWER_APPROVAL, // CMS reviewed and proposed terms — waiting for borrower to confirm
    ACTIVE,                     // borrower confirmed → live on marketplace, accepting investor offers
    FUNDED,                     // fully funded by investors
    REPAYING,                   // borrower making repayments
    COMPLETED,                  // fully repaid
    DEFAULTED,                  // borrower defaulted
    REJECTED,                   // rejected by CMS during underwriting
    CANCELLED                   // cancelled by borrower (declined proposed terms or voluntary withdrawal)
}
