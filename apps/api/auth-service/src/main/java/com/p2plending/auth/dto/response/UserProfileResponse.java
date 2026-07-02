package com.p2plending.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.p2plending.auth.domain.enums.AccountType;
import com.p2plending.auth.domain.enums.BusinessType;
import com.p2plending.auth.domain.enums.Gender;
import com.p2plending.auth.domain.enums.KycStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserProfileResponse {

    // ── User fields ──
    private String id;
    private String phone;
    private String email;
    private KycStatus kycStatus;
    /** INDIVIDUAL mặc định; BUSINESS/ENTERPRISE sau khi hồ sơ doanh nghiệp được duyệt. */
    private AccountType accountType;
    private LocalDateTime createdAt;

    // ── Hồ sơ doanh nghiệp (null nếu chưa nộp) ──
    private KycStatus businessProfileStatus;
    private BusinessType businessType;
    private String businessName;
    private String businessRejectReason;

    // ── KYC fields (null nếu chưa nộp KYC) ──
    private String fullName;
    private Gender gender;
    private String cccdNumber;
    private LocalDate dateOfBirth;
    private String permanentAddress;
    private String hometown;
    private LocalDate issueDate;
    private String issuingAuthority;
    private LocalDate expiryDate;
    private KycStatus kycSubmissionStatus;
    private LocalDateTime kycSubmittedAt;
}
