package com.p2plending.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.p2plending.auth.domain.enums.BusinessType;
import com.p2plending.auth.domain.enums.KycStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Hồ sơ doanh nghiệp — trả cho app (GET /api/auth/business-profile) và CMS (internal). */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BusinessProfileResponse {

    private String id;
    private String userId;
    private BusinessType businessType;
    private String businessName;
    private String registrationNumber;
    private String taxCode;
    private LocalDate issueDate;
    private String issuedBy;
    private String headOfficeAddress;
    private String businessSector;
    private String representativeName;
    private String representativeCccd;
    private String licenseImageId;
    private String licenseExtra1ImageId;
    private String licenseExtra2ImageId;
    private KycStatus status;
    private String rejectReason;
    private String aiVerdict;
    private String aiSummary;
    private String reviewedBy;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;

    /** true nếu CCCD người đại diện KHÁC CCCD eKYC của chủ tài khoản — flag cho admin soi kỹ. */
    private Boolean representativeMismatch;
}
