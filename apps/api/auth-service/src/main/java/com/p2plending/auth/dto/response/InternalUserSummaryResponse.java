package com.p2plending.auth.dto.response;

import com.p2plending.auth.domain.enums.KycStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class InternalUserSummaryResponse {
    private String userId;
    private String email;
    private String fullName;
    private String cccdNumber;
    private String phone;
    private String role;
    private KycStatus kycStatus;
    private String accountStatus;
    private boolean blacklisted;
    private LocalDateTime blacklistedAt;
    private String blacklistSource;
    private String blacklistReason;
    private LocalDateTime createdAt;
    /** Ngày sinh từ KYC đã duyệt — null nếu chưa eKYC hoặc KYC chưa APPROVED. */
    private LocalDate dateOfBirth;
    private String gender;
    private String permanentAddress;
    private String hometown;
    private LocalDate issueDate;
    private String issuingAuthority;
    private LocalDate expiryDate;
    private String frontImageId;
    private String backImageId;
    private String portraitImageId;
}
