package com.p2plending.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RegisterInitResponse {
    private String message;
    private String otp; // only populated in mock mode
}
