package com.p2plending.payment.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LinkedBankResponse {
    private String id;
    private String ownerType;
    private String bankCode;
    private String bankName;
    private String bankAccountNo;
    private String accountName;
    private boolean isDefault;
}
