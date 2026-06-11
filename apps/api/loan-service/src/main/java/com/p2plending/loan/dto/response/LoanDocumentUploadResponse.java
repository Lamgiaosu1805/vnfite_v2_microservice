package com.p2plending.loan.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoanDocumentUploadResponse {
    private String fileId;
    private String fileName;
}
