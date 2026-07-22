package com.p2plending.auth.dto.request.vwork;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.p2plending.auth.domain.enums.Gender;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class CustomerSyncRequest {
    @JsonProperty("app_code")
    private String appCode;

    @JsonProperty("customers")
    private List<CustomerDto> customerDtos;

    @Data
    @Builder
    public static class CustomerDto {
        @JsonProperty("phone_number")
        private String phoneNumber;

        @JsonProperty("external_id")
        private String externalId;

        @JsonProperty("is_kyc")
        private boolean isKyc;

        @JsonProperty("full_name")
        private String fullName;

        @JsonProperty("date_of_birth")
        @JsonFormat(pattern = "yyyy-MM-dd", timezone = "Asia/Ho_Chi_Minh")
        private LocalDate dateOfBirth;

        @JsonProperty("gender")
        private Gender gender;

        @JsonProperty("id_number")
        private String idNumber;

        @JsonProperty("id_type")
        private String idType;

        @JsonProperty("id_issued_date")
        @JsonFormat(pattern = "yyyy-MM-dd", timezone = "Asia/Ho_Chi_Minh")
        private LocalDate idIssuedDate;

        @JsonProperty("id_issued_place")
        private String idIssuedPlace;

        @JsonProperty("address")
        private String address;

        @JsonProperty("created_at")
        private String createdAt;
    }
}