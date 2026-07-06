package com.p2plending.fec.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FecLeadStatusCallbackRequest {
    @JsonProperty("leadgen_id")
    private String leadgenId;
    @JsonProperty("request_time")
    private String requestTime;
    private String status;
    private String remark;
    @JsonProperty("offer_amt")
    private Long offerAmt;
    @JsonProperty("cash_amt")
    private Long cashAmt;
    @JsonProperty("insurance_amt")
    private Long insuranceAmt;
    @JsonProperty("topup_amt")
    private Long topupAmt;
    @JsonProperty("referral_code")
    private String referralCode;
    @JsonProperty("app_id")
    private String appId;
    @JsonProperty("app_type")
    private String appType;
}
