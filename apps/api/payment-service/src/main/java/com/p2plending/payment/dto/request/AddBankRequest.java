package com.p2plending.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AddBankRequest {
    @NotBlank
    private String bankCode;
    @NotBlank
    private String bankName;
    @NotBlank
    private String bankAccountNo;
    /** Nếu null, payment-service sẽ tự xác minh tên qua TIKLUY → MB Bank */
    private String accountName;
    private boolean isDefault;
}
