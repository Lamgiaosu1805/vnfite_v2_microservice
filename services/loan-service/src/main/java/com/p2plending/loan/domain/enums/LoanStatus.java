package com.p2plending.loan.domain.enums;

public enum LoanStatus {
    PENDING,    // awaiting admin approval
    ACTIVE,     // accepting investor offers
    FUNDED,     // fully funded, awaiting disbursement
    REPAYING,   // borrower making repayments
    COMPLETED,  // fully repaid
    DEFAULTED   // borrower defaulted
}
