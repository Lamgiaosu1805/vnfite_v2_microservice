package com.p2plending.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthResponse {

    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("refresh_token")
    private String refreshToken;

    @JsonProperty("token_type")
    @Builder.Default
    private String tokenType = "Bearer";

    @JsonProperty("expires_in")
    private long expiresIn;

    private UserResponse user;
}
