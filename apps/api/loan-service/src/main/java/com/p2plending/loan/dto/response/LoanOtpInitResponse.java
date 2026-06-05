package com.p2plending.loan.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoanOtpInitResponse {
    private String message;
    /** Chỉ trả về khi app.otp.mock=true (môi trường dev/test). */
    private String otp;
}
