package com.p2plending.auth.dto.request.vwork;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.p2plending.auth.domain.enums.Gender;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class UpsertCustomerRequest {
    @JsonProperty("app_code")
    private String appCode;

    @JsonProperty("phone_number")
    private String phoneNumber;

    @JsonProperty("external_id")
    private String externalId;

    @JsonProperty("ref_code")
    private String refCode;

    @JsonProperty("type")
    private String type;

    @JsonProperty("full_name")
    private String fullName;

    @JsonProperty("id_number")
    private String legalId;

    @JsonProperty("id_type")
    private String typeId;

    @JsonProperty("gender")
    private Gender gender;

    @JsonProperty("date_of_birth")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate birthday;

    @JsonProperty("id_issued_date")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate legalIssueDate;

    @JsonProperty("id_issued_place")
    private String legalPlace;

    @JsonProperty("address")
    private String address;

    @JsonProperty("id_front_url")
    private String frontImgPath;

    @JsonProperty("id_back_url")
    private String backImgPath;

    @JsonProperty("selfie_url")
    private String selfiePath;
}
