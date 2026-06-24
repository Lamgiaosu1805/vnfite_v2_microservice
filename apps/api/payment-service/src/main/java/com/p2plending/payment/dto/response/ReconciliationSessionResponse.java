package com.p2plending.payment.dto.response;

import com.p2plending.payment.domain.entity.ReconciliationSession;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class ReconciliationSessionResponse {
    private String id;
    private LocalDate reconDate;
    private String status;
    private int totalItems;
    private int openItems;
    private String runBy;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ReconciliationSessionResponse from(ReconciliationSession s) {
        return ReconciliationSessionResponse.builder()
                .id(s.getId())
                .reconDate(s.getReconDate())
                .status(s.getStatus())
                .totalItems(s.getTotalItems())
                .openItems(s.getOpenItems())
                .runBy(s.getRunBy())
                .errorMessage(s.getErrorMessage())
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .build();
    }
}
