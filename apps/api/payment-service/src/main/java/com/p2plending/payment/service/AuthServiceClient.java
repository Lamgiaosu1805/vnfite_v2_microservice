package com.p2plending.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.p2plending.payment.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceClient {

    private final RestTemplate restTemplate;
    private final AppProperties appProperties;

    /**
     * Lấy fullName đã KYC của user từ auth-service.
     * Trả null nếu không tìm thấy hoặc user chưa có fullName.
     */
    public String getUserFullName(String userId) {
        JsonNode body = fetchUser(userId);
        if (body == null) return null;
        JsonNode nameNode = body.path("fullName");
        return nameNode.isNull() || nameNode.isMissingNode() ? null : nameNode.asText();
    }

    /**
     * Lấy số điện thoại của user từ auth-service để gửi OTP rút tiền.
     * Trả null nếu không tìm thấy.
     */
    public String getUserPhone(String userId) {
        JsonNode body = fetchUser(userId);
        if (body == null) return null;
        JsonNode phoneNode = body.path("phone");
        return phoneNode.isNull() || phoneNode.isMissingNode() ? null : phoneNode.asText();
    }

    public boolean isKycApproved(String userId) {
        JsonNode body = fetchUser(userId);
        if (body == null) return false;
        JsonNode kycNode = body.path("kycStatus");
        return !kycNode.isMissingNode()
                && !kycNode.isNull()
                && "APPROVED".equalsIgnoreCase(kycNode.asText());
    }

    private JsonNode fetchUser(String userId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Secret", appProperties.getInternal().getSecret());

            ResponseEntity<JsonNode> resp = restTemplate.exchange(
                    appProperties.getAuth().getServiceUrl() + "/internal/users/" + userId,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    JsonNode.class);

            return resp.getBody();
        } catch (Exception e) {
            log.error("Failed to fetch user userId={}: {}", userId, e.getMessage());
            return null;
        }
    }
}
