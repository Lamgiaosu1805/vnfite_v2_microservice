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

    public boolean isBlacklisted(String userId) {
        JsonNode body = fetchUser(userId);
        return body != null && body.path("blacklisted").asBoolean(false);
    }

    /** Thông tin hồ sơ doanh nghiệp cần cho đối chiếu tên tài khoản ngân hàng ví DN. */
    public record BusinessProfileInfo(String businessType, String businessName, String representativeName,
                                      String status) {
        public boolean isHousehold() {
            return "HOUSEHOLD".equalsIgnoreCase(businessType);
        }
    }

    /**
     * Lấy hồ sơ doanh nghiệp của user từ auth-service ({@code /internal/users/{userId}/business-profile}).
     * Trả null nếu user chưa có hồ sơ hoặc không gọi được — caller quyết định fail-open/closed.
     */
    public BusinessProfileInfo getBusinessProfile(String userId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Secret", appProperties.getInternal().getSecret());

            ResponseEntity<JsonNode> resp = restTemplate.exchange(
                    appProperties.getAuth().getServiceUrl() + "/internal/users/" + userId + "/business-profile",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    JsonNode.class);

            JsonNode body = resp.getBody();
            if (body == null || body.isNull()) return null;
            return new BusinessProfileInfo(
                    text(body, "businessType"),
                    text(body, "businessName"),
                    text(body, "representativeName"),
                    text(body, "status"));
        } catch (Exception e) {
            log.warn("Failed to fetch business profile userId={}: {}", userId, e.getMessage());
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNull() || value.isMissingNode() ? null : value.asText();
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
