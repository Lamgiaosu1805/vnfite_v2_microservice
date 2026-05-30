package com.p2plending.loan.exception;

public class LoanNotFoundException extends RuntimeException {
    public LoanNotFoundException(String id) {
        super("Loan not found: " + id);
    }
}
