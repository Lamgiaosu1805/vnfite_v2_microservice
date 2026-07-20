package com.p2plending.cms.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.p2plending.cms.service.NotificationCampaignClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * CMS quản lý campaign bắn thông báo marketing (gửi ngay hoặc đặt lịch).
 * Proxy sang notification-service — không lưu dữ liệu ở cms_db.
 */
@RestController
@RequestMapping("/cms/notifications/campaigns")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'CONTENT')")
public class MarketingNotificationController {

    private final NotificationCampaignClient campaignClient;

    @GetMapping
    public ResponseEntity<JsonNode> listCampaigns(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(campaignClient.listCampaigns(page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<JsonNode> getCampaign(@PathVariable String id) {
        return ResponseEntity.ok(campaignClient.getCampaign(id));
    }

    @PostMapping
    public ResponseEntity<JsonNode> createCampaign(
            @RequestBody Map<String, Object> body,
            Authentication auth) {
        Map<String, Object> payload = new HashMap<>(body);
        payload.put("createdBy", auth != null ? auth.getName() : null);
        return ResponseEntity.ok(campaignClient.createCampaign(payload));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<JsonNode> cancelCampaign(@PathVariable String id) {
        return ResponseEntity.ok(campaignClient.cancelCampaign(id));
    }
}
