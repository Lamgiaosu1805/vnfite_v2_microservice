package com.p2plending.payment.dto.request;

import lombok.Data;

@Data
public class ManualDepositDecisionRequest {
    private String reason;
    private String reviewedBy;
}
