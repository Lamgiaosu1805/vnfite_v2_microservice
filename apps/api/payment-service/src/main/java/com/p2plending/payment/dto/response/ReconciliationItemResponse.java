package com.p2plending.payment.dto.response;

import com.p2plending.payment.domain.entity.ReconciliationItem;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class ReconciliationItemResponse {
    private String id;
    private String sessionId;
    private String itemType;
    private String severity;
    private String walletId;
    private String transactionId;
    private String referenceId;
    private String externalRef;
    private String vnfiteStatus;
    private String mbStatus;
    private BigDecimal amount;
    private String description;
    private String status;
    private String resolvedBy;
    private LocalDateTime resolvedAt;
    private String resolutionNotes;
    private LocalDateTime createdAt;

    public static ReconciliationItemResponse from(ReconciliationItem i) {
        return ReconciliationItemResponse.builder()
                .id(i.getId())
                .sessionId(i.getSessionId())
                .itemType(i.getItemType())
                .severity(i.getSeverity())
                .walletId(i.getWalletId())
                .transactionId(i.getTransactionId())
                .referenceId(i.getReferenceId())
                .externalRef(i.getExternalRef())
                .vnfiteStatus(i.getVnfiteStatus())
                .mbStatus(i.getMbStatus())
                .amount(i.getAmount())
                .description(i.getDescription())
                .status(i.getStatus())
                .resolvedBy(i.getResolvedBy())
                .resolvedAt(i.getResolvedAt())
                .resolutionNotes(i.getResolutionNotes())
                .createdAt(i.getCreatedAt())
                .build();
    }
}
