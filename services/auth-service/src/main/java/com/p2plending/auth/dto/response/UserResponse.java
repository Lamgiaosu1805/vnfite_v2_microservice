package com.p2plending.auth.dto.response;

import com.p2plending.auth.domain.enums.KycStatus;
import com.p2plending.auth.domain.enums.Role;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UserResponse {
    private Long id;
    private String phone;
    private String email;
    private String fullName;
    private Role role;
    private KycStatus kycStatus;
    private String referralCode;
    private LocalDateTime createdAt;
}
