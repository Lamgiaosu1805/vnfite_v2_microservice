package com.p2plending.auth.dto.response.vwork;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ReferralResponse {
    @JsonProperty("ref_code")
    private String refCode;

    private Boolean exists;
    private String type;
    private Info info;

    @Data
    public static class Info {
        @JsonProperty("agent_code")
        private String agentCode;

        @JsonProperty("full_name")
        private String fullname;

        @JsonProperty("phone_number")
        private String phoneNumber;
    }
}
