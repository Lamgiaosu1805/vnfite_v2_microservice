package com.p2plending.notification.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Gọi external push notification service tại service.vnfite.com.vn.
 *
 * API hỗ trợ:
 *   POST /push-notification/v2/notification/pushNotification       — 1 token
 *   POST /push-notification/v2/notification/pushMultiNotification  — nhiều token
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PushNotificationClient {

    private final RestTemplate restTemplate;

    @Value("${push.notification.base-url:https://service.vnfite.com.vn/push-notification/v2/notification}")
    private String baseUrl;

    @Value("${push.notification.alias:vnfite}")
    private String alias;

    @Value("${cms.sources.auth-url:http://auth-service:8081}")
    private String authServiceUrl;

    @Value("${cms.sources.internal-secret}")
    private String internalSecret;

    /**
     * Gửi push notification đến 1 FCM token.
     *
     * @param fcmToken token của thiết bị
     * @param title    tiêu đề thông báo
     * @param body     nội dung thông báo
     * @param data     payload kèm theo (nullable)
     */
    public void pushToToken(String fcmToken, String title, String body, Map<String, Object> data) {
        if (fcmToken == null || fcmToken.isBlank()) {
            log.warn("[Push] FCM token rỗng — bỏ qua push notification");
            return;
        }

        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("alias", alias);
        payload.put("fcmToken", fcmToken);
        payload.put("title", title);
        payload.put("body", body);
        if (data != null) payload.put("data", data);

        doPost(baseUrl + "/pushNotification", payload);
    }

    /**
     * Lấy FCM token của user từ auth-service rồi push notification.
     * Trả về true nếu push thành công, false nếu không có token hoặc lỗi.
     */
    public boolean pushToUser(String userId, String title, String body, Map<String, Object> data) {
        Optional<String> fcmToken = getFcmTokenByUserId(userId);
        if (fcmToken.isEmpty()) {
            log.info("[Push] userId={} chưa có FCM token — bỏ qua push", userId);
            return false;
        }
        pushToToken(fcmToken.get(), title, body, data);
        return true;
    }

    /**
     * Push đến nhiều token (broadcast / nhóm).
     */
    public void pushToTokens(List<String> fcmTokens, String title, String body, Map<String, Object> data) {
        if (fcmTokens == null || fcmTokens.isEmpty()) return;

        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("alias", alias);
        payload.put("tokens", fcmTokens);
        payload.put("title", title);
        payload.put("body", body);
        if (data != null) payload.put("data", data);

        doPost(baseUrl + "/pushMultiNotification", payload);
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    private Optional<String> getFcmTokenByUserId(String userId) {
        try {
            String url = authServiceUrl + "/internal/users/" + userId + "/fcm-token";
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Secret", internalSecret);
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);

            if (response.getStatusCode() == HttpStatus.NO_CONTENT || response.getBody() == null) {
                return Optional.empty();
            }
            Object token = response.getBody().get("fcmToken");
            return Optional.ofNullable(token).map(Object::toString).filter(s -> !s.isBlank());
        } catch (Exception ex) {
            log.warn("[Push] Không lấy được FCM token userId={}: {}", userId, ex.getMessage());
            return Optional.empty();
        }
    }

    private void doPost(String url, Map<String, Object> payload) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            log.info("[Push] → {} | status={}", url, response.getStatusCode());
        } catch (Exception ex) {
            log.error("[Push] Lỗi gọi push service {}: {}", url, ex.getMessage());
        }
    }
}
