package com.p2plending.auth.domain.model;

import com.p2plending.auth.domain.enums.Gender;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PendingKycData {
    private String userId;
    private String cccdNumber;
    private String fullName;
    private Gender gender;
    private LocalDate dateOfBirth;
    private String permanentAddress;
    private String hometown;
    private LocalDate issueDate;
    private String issuingAuthority;
    private LocalDate expiryDate;   // null = không thời hạn
    private String frontImageId;
    private String backImageId;
    private String portraitImageId;
    private String otp;
}
