package com.p2plending.cms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2plending.cms.exception.SourceServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * Proxy sang notification-service — quản lý campaign bắn thông báo marketing
 * (gửi ngay hoặc đặt lịch lặp lại hằng ngày).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationCampaignClient {

    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${cms.sources.notification-url:http://notification-service:8084}")
    private String notificationServiceUrl;

    @Value("${cms.sources.internal-secret}")
    private String internalSecret;

    public JsonNode createCampaign(Map<String, Object> body) {
        String url = notificationServiceUrl + "/internal/notification-campaigns";
        return exchangeForJson(url, HttpMethod.POST, body);
    }

    public JsonNode listCampaigns(int page, int size) {
        String url = UriComponentsBuilder.fromHttpUrl(notificationServiceUrl)
                .path("/internal/notification-campaigns")
                .queryParam("page", page)
                .queryParam("size", size)
                .toUriString();
        return exchangeForJson(url, HttpMethod.GET, null);
    }

    public JsonNode getCampaign(String id) {
        String url = UriComponentsBuilder.fromHttpUrl(notificationServiceUrl)
                .path("/internal/notification-campaigns/{id}")
                .buildAndExpand(id)
                .toUriString();
        return exchangeForJson(url, HttpMethod.GET, null);
    }

    public JsonNode cancelCampaign(String id) {
        String url = UriComponentsBuilder.fromHttpUrl(notificationServiceUrl)
                .path("/internal/notification-campaigns/{id}/cancel")
                .buildAndExpand(id)
                .toUriString();
        return exchangeForJson(url, HttpMethod.POST, null);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private JsonNode exchangeForJson(String url, HttpMethod method, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(INTERNAL_SECRET_HEADER, internalSecret);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Object> entity = new HttpEntity<>(body, headers);

        ResponseEntity<String> response;
        try {
            response = restTemplate.exchange(url, method, entity, String.class);
        } catch (RestClientResponseException ex) {
            String message = sourceErrorMessage(ex);
            log.warn("[NotificationCampaign] HTTP error từ {}: status={}, message={}",
                    url, ex.getStatusCode(), message);
            throw new SourceServiceException(ex.getStatusCode(), message);
        } catch (Exception ex) {
            log.error("[NotificationCampaign] Không kết nối được {}: {} — {}",
                    url, ex.getClass().getSimpleName(), ex.getMessage());
            throw new SourceServiceException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Không thể kết nối với máy chủ. Vui lòng thử lại.");
        }

        String responseBody = response.getBody();
        if (responseBody == null || responseBody.isBlank()) {
            return objectMapper.nullNode();
        }
        try {
            return objectMapper.readTree(responseBody);
        } catch (Exception ex) {
            log.error("[NotificationCampaign] Không parse được JSON từ {}: {}", url, ex.getMessage());
            throw new SourceServiceException(HttpStatus.BAD_GATEWAY, "Phản hồi từ service nguồn không hợp lệ.");
        }
    }

    private String sourceErrorMessage(RestClientResponseException ex) {
        String body = ex.getResponseBodyAsString();
        if (body == null || body.isBlank()) {
            return "Service nguồn trả lỗi %s".formatted(ex.getStatusCode().value());
        }
        try {
            JsonNode node = objectMapper.readTree(body);
            if (node.hasNonNull("details") && node.get("details").isArray() && node.get("details").size() > 0) {
                StringBuilder sb = new StringBuilder();
                node.get("details").forEach(d -> sb.append(d.asText()).append(" "));
                return sb.toString().trim();
            }
            if (node.hasNonNull("message")) return node.get("message").asText();
            if (node.hasNonNull("detail")) return node.get("detail").asText();
            if (node.hasNonNull("error")) return node.get("error").asText();
        } catch (Exception ignored) {
            // fallthrough to raw body below
        }
        return body;
    }
}
