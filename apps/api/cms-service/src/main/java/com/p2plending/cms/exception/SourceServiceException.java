package com.p2plending.cms.exception;

import org.springframework.http.HttpStatusCode;

public class SourceServiceException extends RuntimeException {

    private final HttpStatusCode statusCode;

    public SourceServiceException(HttpStatusCode statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public HttpStatusCode getStatusCode() {
        return statusCode;
    }
}
