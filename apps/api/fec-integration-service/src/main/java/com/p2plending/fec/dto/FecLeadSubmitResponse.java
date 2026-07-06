package com.p2plending.fec.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FecLeadSubmitResponse {
    private String transId;
    private Integer code;
    private String description;
    private String leadStatus;
    private String leadGenId;
    private String onboardingLink;
    private boolean responseSignatureValid;
}
