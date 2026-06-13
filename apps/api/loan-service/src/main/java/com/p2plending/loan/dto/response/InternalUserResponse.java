package com.p2plending.loan.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class InternalUserResponse {
    private String userId;
    private String fullName;
    private String phone;
    private String cccdNumber;
    private String kycStatus;
}
