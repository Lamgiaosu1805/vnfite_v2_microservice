package com.p2plending.cms.dto.response;

import com.p2plending.cms.domain.enums.UserAccountStatus;
import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class UserSummaryResponse {
    private String userId;
    private String email;
    private String fullName;
    private String phone;
    private String role;
    private String kycStatus;
    private UserAccountStatus accountStatus;
    private LocalDateTime createdAt;
}
