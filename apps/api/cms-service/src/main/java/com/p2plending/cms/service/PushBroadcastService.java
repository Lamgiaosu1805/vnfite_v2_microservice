package com.p2plending.cms.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gửi push notification đến tất cả thiết bị đang đăng ký — dùng cho CMS test broadcast.
 *
 * Flow:
 *   1. Lấy tất cả FCM token từ auth-service (internal API)
 *   2. Gọi service.vnfite.com.vn pushMultiNotification
 *   3. Trả về kết quả: số token đích, status push service
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PushBroadcastService {

    private final RestTemplate   restTemplate;
    private final SourceServiceClient sourceServiceClient;

    @Value("${cms.sources.auth-url:http://auth-service:8081}")
    private String authServiceUrl;

    @Value("${cms.sources.internal-secret}")
    private String internalSecret;

    @Value("${push.notification.base-url:https://service.vnfite.com.vn/push-notification/v2/notification}")
    private String pushBaseUrl;

    @Value("${push.notification.alias:vnfite}")
    private String pushAlias;

    /**
     * Lấy tất cả FCM token từ auth-service.
     */
    public List<String> getAllFcmTokens() {
        try {
            String url = authServiceUrl + "/internal/users/fcm-tokens-all";
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Secret", internalSecret);
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);

            if (response.getBody() == null) return List.of();

            Object tokens = response.getBody().get("tokens");
            if (tokens instanceof List<?> list) {
                return list.stream()
                        .filter(t -> t instanceof String s && !s.isBlank())
                        .map(Object::toString)
                        .toList();
            }
            return List.of();
        } catch (Exception ex) {
            log.error("[PushBroadcast] Không lấy được danh sách FCM token: {}", ex.getMessage());
            throw new RuntimeException("Không thể lấy danh sách FCM token từ auth-service: " + ex.getMessage());
        }
    }

    /**
     * Gửi push notification đến tất cả thiết bị.
     *
     * @return Map gồm { sentTo (số token), pushResponse (raw response từ push service) }
     */
    public Map<String, Object> broadcastToAll(String title, String body, Map<String, Object> data) {
        List<String> tokens = getAllFcmTokens();

        if (tokens.isEmpty()) {
            log.info("[PushBroadcast] Không có thiết bị nào đang đăng ký FCM token");
            return Map.of("sentTo", 0, "message", "Không có thiết bị nào đang đăng ký.");
        }

        log.info("[PushBroadcast] Gửi '{}' đến {} thiết bị", title, tokens.size());

        // Gửi batch qua pushMultiNotification
        String pushResponse = pushMulti(tokens, title, body, data);

        return Map.of(
                "sentTo", tokens.size(),
                "pushResponse", pushResponse != null ? pushResponse : "ok"
        );
    }

    // ─── Push API helpers ─────────────────────────────────────────────────────

    private String pushMulti(List<String> tokens, String title, String body, Map<String, Object> data) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("alias", pushAlias);
        payload.put("tokens", tokens);
        payload.put("title", title);
        payload.put("body", body);
        if (data != null && !data.isEmpty()) payload.put("data", data);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    pushBaseUrl + "/pushMultiNotification", HttpMethod.POST, entity, String.class);
            log.info("[PushBroadcast] Push service status={}", response.getStatusCode());
            return response.getBody();
        } catch (Exception ex) {
            log.error("[PushBroadcast] Lỗi gọi push service: {}", ex.getMessage());
            throw new RuntimeException("Push service lỗi: " + ex.getMessage());
        }
    }
}
