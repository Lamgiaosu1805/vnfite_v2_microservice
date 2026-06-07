package com.p2plending.cms.controller;

import com.p2plending.cms.service.PushBroadcastService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * CMS Notification API — quản lý & test push notification.
 */
@RestController
@RequestMapping("/cms/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

    private final PushBroadcastService pushBroadcastService;

    /**
     * GET /cms/notifications/fcm-devices
     * Trả về số lượng thiết bị đang có FCM token.
     * Dùng để hiện thị "X thiết bị đang đăng ký" trên CMS trước khi bắn test.
     */
    @GetMapping("/fcm-devices")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> getFcmDeviceCount() {
        int count = pushBroadcastService.getAllFcmTokens().size();
        return ResponseEntity.ok(Map.of("count", count));
    }

    /**
     * POST /cms/notifications/test-push
     * Gửi push notification test đến tất cả thiết bị đang đăng ký.
     *
     * Body: { "title": "...", "body": "..." }
     * Response: { "sentTo": N, "pushResponse": "..." }
     */
    @PostMapping("/test-push")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> sendTestPush(
            @RequestBody Map<String, String> request,
            Authentication auth) {

        String title = request.getOrDefault("title", "Thông báo từ VNFITE").trim();
        String body  = request.getOrDefault("body",  "Đây là thông báo test từ hệ thống.").trim();

        if (title.isBlank()) title = "Thông báo từ VNFITE";
        if (body.isBlank())  body  = "Đây là thông báo test từ hệ thống.";

        log.info("[CMS] Admin {} gửi test push — title='{}' body='{}'",
                auth != null ? auth.getName() : "unknown", title, body);

        Map<String, Object> result = pushBroadcastService.broadcastToAll(title, body, null);
        return ResponseEntity.ok(result);
    }
}
