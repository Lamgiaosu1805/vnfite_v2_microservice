package com.p2plending.auth.exception;

/** Ném khi user đã có hồ sơ doanh nghiệp PENDING/APPROVED mà nộp thêm — map 409 CONFLICT. */
public class BusinessProfileConflictException extends RuntimeException {
    public BusinessProfileConflictException(String message) {
        super(message);
    }
}
