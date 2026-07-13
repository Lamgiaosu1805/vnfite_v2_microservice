package com.p2plending.cms.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.p2plending.cms.security.CmsPrincipal;
import com.p2plending.cms.service.SourceServiceClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/cms/security/otp-ip-unblock-requests")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'CUSTOMER_SUPPORT')")
public class OtpSecurityController {
    private final SourceServiceClient sourceServiceClient;

    @GetMapping
    public ResponseEntity<JsonNode> list(@RequestParam(required = false) String status,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(sourceServiceClient.getOtpIpUnblockRequests(status, Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
    }

    @PostMapping("/{requestId}/decision")
    public ResponseEntity<JsonNode> decide(@PathVariable String requestId,
                                            @RequestBody Map<String, Object> body,
                                            @AuthenticationPrincipal CmsPrincipal operator) {
        boolean approved = Boolean.TRUE.equals(body.get("approved"));
        String reason = body.get("reason") instanceof String value ? value : null;
        return ResponseEntity.ok(sourceServiceClient.decideOtpIpUnblockRequest(requestId, approved, reason, operator.displayName()));
    }
}
