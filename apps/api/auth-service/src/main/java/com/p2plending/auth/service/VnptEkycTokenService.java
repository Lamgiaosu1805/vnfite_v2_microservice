package com.p2plending.auth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.p2plending.auth.dto.response.VnptEkycTokenResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Lấy access_token VNPT eKYC bằng username/password đặt ở backend (không lộ ra app).
 * Trả về { accessToken, tokenId, tokenKey } để mobile khởi tạo SDK.
 * Kế thừa cách làm của hệ thống cũ (ms001 getTokenVnpt): grant_type=password, client adminapp.
 */
@Service
@Slf4j
public class VnptEkycTokenService {

    @Value("${app.vnpt-ekyc.token-url:https://api.idg.vnpt.vn/auth/oauth/token}")
    private String tokenUrl;

    @Value("${app.vnpt-ekyc.username:}")
    private String username;

    @Value("${app.vnpt-ekyc.password:}")
    private String password;

    @Value("${app.vnpt-ekyc.token-id:}")
    private String tokenId;

    @Value("${app.vnpt-ekyc.token-key:}")
    private String tokenKey;

    @Value("${app.vnpt-ekyc.client-id:adminapp}")
    private String clientId;

    @Value("${app.vnpt-ekyc.client-secret:password}")
    private String clientSecret;

    private final RestTemplate restTemplate = new RestTemplate();

    public VnptEkycTokenResponse getToken() {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            log.error("VNPT eKYC credentials chưa cấu hình (app.vnpt-ekyc.username/password)");
            throw new IllegalStateException("Dịch vụ định danh điện tử chưa sẵn sàng. Vui lòng thử lại sau.");
        }

        Map<String, Object> body = Map.of(
                "username",      username,
                "password",      password,
                "client_id",     clientId,
                "grant_type",    "password",
                "client_secret", clientSecret
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    tokenUrl,
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    JsonNode.class
            );

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("VNPT token thất bại status={} body={}", response.getStatusCode(), response.getBody());
                throw new IllegalStateException("Không lấy được phiên định danh điện tử. Vui lòng thử lại.");
            }

            JsonNode accessTokenNode = response.getBody().get("access_token");
            if (accessTokenNode == null || accessTokenNode.asText().isBlank()) {
                log.warn("VNPT token response thiếu access_token: {}", response.getBody());
                throw new IllegalStateException("Không lấy được phiên định danh điện tử. Vui lòng thử lại.");
            }

            // SDK VNPT (cả iOS lẫn Android) nhận accessToken định dạng "Bearer <token>"
            return VnptEkycTokenResponse.builder()
                    .accessToken("Bearer " + accessTokenNode.asText())
                    .tokenId(tokenId)
                    .tokenKey(tokenKey)
                    .build();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("Lỗi gọi VNPT oauth token: {}", e.getMessage(), e);
            throw new IllegalStateException("Không kết nối được dịch vụ định danh điện tử. Vui lòng thử lại.");
        }
    }
}
