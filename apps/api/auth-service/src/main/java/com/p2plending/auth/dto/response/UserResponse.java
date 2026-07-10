package com.p2plending.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.p2plending.auth.domain.enums.AccountType;
import com.p2plending.auth.domain.enums.BusinessType;
import com.p2plending.auth.domain.enums.KycStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserResponse {
    private String id;
    private String phone;
    private String email;
    private String fullName;
    private KycStatus kycStatus;
    private AccountType accountType;
    private LocalDateTime createdAt;

    // ── Hồ sơ doanh nghiệp (null nếu chưa nộp) — cần có ở login/refresh để app
    //    hiện được ví/toggle doanh nghiệp mà không phải gọi thêm GET /api/auth/me ──
    private KycStatus businessProfileStatus;
    private BusinessType businessType;
    private String businessName;
    private String businessRejectReason;
}
