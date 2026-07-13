package com.p2plending.auth.exception;

public class OtpIpBlockedException extends RuntimeException {
    public OtpIpBlockedException() {
        super("Mạng của bạn đang tạm bị chặn gửi OTP do có dấu hiệu bất thường. Bạn có thể gửi yêu cầu để VNFITE hỗ trợ kiểm tra.");
    }
}
