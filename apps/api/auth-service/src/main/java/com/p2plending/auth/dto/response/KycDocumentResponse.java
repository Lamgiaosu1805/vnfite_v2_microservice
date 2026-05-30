package com.p2plending.auth.dto.response;

import com.p2plending.auth.domain.enums.DocType;
import com.p2plending.auth.domain.enums.KycStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class KycDocumentResponse {
    private String id;
    private String userId;
    private DocType docType;
    private String docUrl;
    private KycStatus status;
    private LocalDateTime createdAt;
}
