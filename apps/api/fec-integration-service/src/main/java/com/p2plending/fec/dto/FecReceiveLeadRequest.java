package com.p2plending.fec.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FecReceiveLeadRequest {
    private String transId;
    private String fullName;
    private String phoneNumber;
    private String nid;
    private String dob;
    private String email;
    private Long loanAmount;
    private Integer tenor;
    private String leadSource;
    private String agentCode;
    private String consentType;
    private String consentTickbox;
    private String consentContent;
}
