package com.p2plending.notification.controller;

import com.p2plending.notification.dto.NotificationCampaignRequest;
import com.p2plending.notification.dto.NotificationCampaignResponse;
import com.p2plending.notification.dto.PagedResponse;
import com.p2plending.notification.service.NotificationCampaignService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/**
 * Internal API cho cms-service quản lý campaign thông báo marketing.
 * Bảo vệ bằng header X-Internal-Secret (không dùng JWT — chỉ service nội bộ gọi).
 */
@RestController
@RequestMapping("/internal/notification-campaigns")
@RequiredArgsConstructor
public class InternalNotificationCampaignController {

    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";

    private final NotificationCampaignService campaignService;

    @Value("${cms.sources.internal-secret}")
    private String internalSecret;

    @PostMapping
    public ResponseEntity<NotificationCampaignResponse> createCampaign(
            @RequestHeader(INTERNAL_SECRET_HEADER) String secret,
            @Valid @RequestBody NotificationCampaignRequest request) {
        requireInternalSecret(secret);
        return ResponseEntity.ok(campaignService.createCampaign(request));
    }

    @GetMapping
    public ResponseEntity<PagedResponse<NotificationCampaignResponse>> listCampaigns(
            @RequestHeader(INTERNAL_SECRET_HEADER) String secret,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireInternalSecret(secret);
        return ResponseEntity.ok(campaignService.listCampaigns(page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<NotificationCampaignResponse> getCampaign(
            @RequestHeader(INTERNAL_SECRET_HEADER) String secret,
            @PathVariable String id) {
        requireInternalSecret(secret);
        return ResponseEntity.ok(campaignService.getCampaign(id));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<NotificationCampaignResponse> cancelCampaign(
            @RequestHeader(INTERNAL_SECRET_HEADER) String secret,
            @PathVariable String id) {
        requireInternalSecret(secret);
        return ResponseEntity.ok(campaignService.cancelCampaign(id));
    }

    private void requireInternalSecret(String secret) {
        if (secret == null || !secret.equals(internalSecret)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid internal secret");
        }
    }
}
