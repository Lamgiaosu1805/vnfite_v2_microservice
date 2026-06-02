package com.p2plending.auth.dto.response;

import com.p2plending.auth.domain.enums.KycStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UserResponse {
    private String id;
    private String phone;
    private String email;
    private String fullName;
    private KycStatus kycStatus;
    private LocalDateTime createdAt;
}
