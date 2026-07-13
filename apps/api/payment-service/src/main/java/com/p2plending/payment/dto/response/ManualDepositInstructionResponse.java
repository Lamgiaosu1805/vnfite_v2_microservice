package com.p2plending.payment.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ManualDepositInstructionResponse {
    private String bankName;
    private String accountNo;
    private String accountName;
    private String instruction;
}
