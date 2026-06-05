package com.p2plending.auth.exception;

public class OtpRateLimitException extends RuntimeException {

    private final long retryAfterSeconds;

    public OtpRateLimitException(long retryAfterSeconds) {
        super("Bạn đã yêu cầu OTP quá nhiều lần. Vui lòng thử lại sau " + retryAfterSeconds + " giây.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
