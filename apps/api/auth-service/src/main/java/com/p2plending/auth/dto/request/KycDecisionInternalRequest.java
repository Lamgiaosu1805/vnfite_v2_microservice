package com.p2plending.auth.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Request body cho internal endpoint PUT /internal/users/{id}/kyc-decision.
 * Chỉ CMS service gọi (bảo vệ bằng X-Internal-Secret header).
 */
@Data
public class KycDecisionInternalRequest {
    @NotNull(message = "approved là bắt buộc")
    private boolean approved;
    private String reason;
}
