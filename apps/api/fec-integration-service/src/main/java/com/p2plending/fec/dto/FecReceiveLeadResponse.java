package com.p2plending.fec.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FecReceiveLeadResponse {
    @JsonProperty("TransID")
    private String transId;
    @JsonProperty("DateTime")
    private String dateTime;
    @JsonProperty("Code")
    private Integer code;
    @JsonProperty("Description")
    private String description;
    @JsonProperty("Data")
    private Data data;

    @Getter
    @Setter
    public static class Data {
        @JsonProperty("TransID")
        private String transId;
        @JsonProperty("LeadStatus")
        private String leadStatus;
        @JsonProperty("LeadGenID")
        private String leadGenId;
        @JsonProperty("Link")
        private String link;
    }
}
