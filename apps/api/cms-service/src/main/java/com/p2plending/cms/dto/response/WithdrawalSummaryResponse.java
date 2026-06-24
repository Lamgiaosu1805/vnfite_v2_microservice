package com.p2plending.cms.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class WithdrawalSummaryResponse {

    private String id;
    private String userId;
    private String customerPhone;
    private String customerName;
    private BigDecimal amount;
    private String status;
    private String statusLabel;
    private String bankName;
    private String bankAccountNo;
    private String transferRef;
    private String mbFtNumber;
    private String providerTransferRef;
    private String rejectReason;
    private String failureReason;
    private int retryCount;
    private int maxRetries;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
