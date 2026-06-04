package com.p2plending.auth.dto.response;

import com.p2plending.auth.domain.enums.KycStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class InternalUserSummaryResponse {
    private String userId;
    private String email;
    private String fullName;
    private String phone;
    private String role;
    private KycStatus kycStatus;
    private String accountStatus;
    private LocalDateTime createdAt;
}
