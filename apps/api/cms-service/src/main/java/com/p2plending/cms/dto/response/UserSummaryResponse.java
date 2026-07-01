package com.p2plending.cms.dto.response;

import com.p2plending.cms.domain.enums.UserAccountStatus;
import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class UserSummaryResponse {
    private String userId;
    private String email;
    private String fullName;
    private String cccdNumber;
    private String phone;
    private String role;
    private String kycStatus;
    private UserAccountStatus accountStatus;
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
