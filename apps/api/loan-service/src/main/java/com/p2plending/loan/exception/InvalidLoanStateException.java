package com.p2plending.loan.exception;

public class InvalidLoanStateException extends RuntimeException {
    public InvalidLoanStateException(String message) {
        super(message);
    }
}
