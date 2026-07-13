package com.p2plending.payment.dto.response;

import com.p2plending.payment.domain.entity.ManualDepositRequest;
import com.p2plending.payment.domain.enums.ManualDepositStatus;
import com.p2plending.payment.domain.enums.WalletOwnerType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class ManualDepositResponse {
    private String id;
    private String userId;
    private String walletId;
    private WalletOwnerType ownerType;
    private BigDecimal amount;
    private String billFileId;
    private String billFileName;
    private ManualDepositStatus status;
    private String rejectionReason;
    private String reviewedBy;
    private LocalDateTime reviewedAt;
    private String walletTransactionId;
    private LocalDateTime createdAt;

    public static ManualDepositResponse from(ManualDepositRequest request) {
        return ManualDepositResponse.builder()
                .id(request.getId())
                .userId(request.getUserId())
                .walletId(request.getWalletId())
                .ownerType(request.getOwnerType())
                .amount(request.getAmount())
                .billFileId(request.getBillFileId())
                .billFileName(request.getBillFileName())
                .status(request.getStatus())
                .rejectionReason(request.getRejectionReason())
                .reviewedBy(request.getReviewedBy())
                .reviewedAt(request.getReviewedAt())
                .walletTransactionId(request.getWalletTransactionId())
                .createdAt(request.getCreatedAt())
                .build();
    }
}
