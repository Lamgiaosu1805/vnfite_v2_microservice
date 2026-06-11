package com.p2plending.loan.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class LoanDocumentResponse {
    private String id;
    private String docType;
    private String fileId;
    private String fileName;
    private LocalDateTime createdAt;
}
