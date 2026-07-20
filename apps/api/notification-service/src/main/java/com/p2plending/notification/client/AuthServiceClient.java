package com.p2plending.notification.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;

/**
 * Gọi auth-service để lấy danh sách (userId, fcmToken) theo segment — dùng cho
 * campaign thông báo marketing (cần userId để ghi lịch sử in-app cho đúng người).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuthServiceClient {

    private final RestTemplate restTemplate;

    @Value("${cms.sources.auth-url:http://auth-service:8081}")
    private String authServiceUrl;

    @Value("${cms.sources.internal-secret}")
    private String internalSecret;

    public record TokenEntry(String userId, String fcmToken) {}

    /**
     * @param kycStatus null = tất cả segment, ngược lại lọc đúng trạng thái KYC đó
     */
    @SuppressWarnings("unchecked")
    public List<TokenEntry> getTokensBySegment(String kycStatus) {
        try {
            String url = UriComponentsBuilder
                    .fromHttpUrl(authServiceUrl + "/internal/users/fcm-tokens-by-segment")
                    .queryParamIfPresent("kycStatus", java.util.Optional.ofNullable(kycStatus))
                    .toUriString();
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Secret", internalSecret);
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);

            if (response.getBody() == null) return List.of();
            Object rawEntries = response.getBody().get("entries");
            if (!(rawEntries instanceof List<?> list)) return List.of();

            return list.stream()
                    .filter(item -> item instanceof Map)
                    .map(item -> (Map<String, Object>) item)
                    .map(m -> new TokenEntry(
                            String.valueOf(m.get("userId")),
                            String.valueOf(m.get("fcmToken"))))
                    .toList();
        } catch (Exception ex) {
            log.error("[AuthServiceClient] Không lấy được token theo segment kycStatus={}: {}",
                    kycStatus, ex.getMessage());
            throw new RuntimeException("Không thể lấy danh sách người dùng từ auth-service: " + ex.getMessage());
        }
    }
}
