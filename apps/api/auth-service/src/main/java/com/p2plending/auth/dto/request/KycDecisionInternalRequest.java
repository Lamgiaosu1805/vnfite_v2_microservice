package com.p2plending.auth.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Request body cho internal endpoint POST /internal/users/{id}/kyc-decision.
 * Chỉ CMS service gọi (bảo vệ bằng X-Internal-Secret header).
 */
@Data
public class KycDecisionInternalRequest {
    @NotNull(message = "decision là bắt buộc (APPROVED hoặc REJECTED)")
    private String decision;
    private String reason;

    public boolean isApproved() {
        return "APPROVED".equalsIgnoreCase(decision);
    }
}
