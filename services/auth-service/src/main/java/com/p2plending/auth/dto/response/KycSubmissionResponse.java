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
public class KycSubmissionResponse {
    private String id;
    private String userId;
    private String cccdNumber;
    private String fullName;
    private Gender gender;
    private LocalDate dateOfBirth;
    private String permanentAddress;
    private String hometown;
    private LocalDate issueDate;
    private String issuingAuthority;
    private LocalDate expiryDate;
    private String frontImageId;
    private String backImageId;
    private String portraitImageId;
    private KycStatus status;
    private LocalDateTime createdAt;
}
