package com.p2plending.cms.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class KycDecisionRequest {

    @NotNull(message = "Decision is required: APPROVED or REJECTED")
    private Decision decision;

    @Size(max = 500)
    private String reason;

    public enum Decision { APPROVED, REJECTED }
}
