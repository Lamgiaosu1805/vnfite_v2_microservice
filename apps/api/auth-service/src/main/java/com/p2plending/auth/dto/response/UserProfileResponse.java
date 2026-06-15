package com.p2plending.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
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
    private LocalDateTime createdAt;

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
