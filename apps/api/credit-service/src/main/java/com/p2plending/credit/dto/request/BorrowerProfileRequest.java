package com.p2plending.credit.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class BorrowerProfileRequest {

    @NotBlank(message = "userId không được để trống")
    private String userId;

    /** GOV_EMPLOYEE | SALARIED | BUSINESS_OWNER | FREELANCER | OTHER */
    private String occupationType;

    private BigDecimal employmentYears;

    private BigDecimal monthlyIncome;

    /** SINGLE | MARRIED | DIVORCED | WIDOWED */
    private String maritalStatus;

    private Integer dependentsCount;

    /** POSTGRAD | UNIVERSITY | COLLEGE | HIGH_SCHOOL | OTHER */
    private String educationLevel;

    private BigDecimal existingMonthlyDebt;

    private String notes;
}
