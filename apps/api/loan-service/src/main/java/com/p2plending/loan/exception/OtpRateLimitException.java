package com.p2plending.loan.exception;

public class OtpRateLimitException extends RuntimeException {

    private final long retryAfterSeconds;

    public OtpRateLimitException(long retryAfterSeconds) {
        this("Bạn đã yêu cầu OTP quá nhiều lần. Vui lòng thử lại sau " + retryAfterSeconds + " giây.",
                retryAfterSeconds);
    }

    public OtpRateLimitException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
