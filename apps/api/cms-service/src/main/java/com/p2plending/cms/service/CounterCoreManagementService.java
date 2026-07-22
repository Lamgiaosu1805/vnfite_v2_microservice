package com.p2plending.cms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2plending.cms.exception.SourceServiceException;
import com.p2plending.cms.security.CmsPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CounterCoreManagementService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${cms.core.base-url}")
    private String coreBaseUrl;

    @Value("${cms.core.tenant-id}")
    private String tenantId;

    @Value("${cms.core.service-token:}")
    private String serviceToken;

    public JsonNode listBranches(CmsPrincipal operator) {
        return exchange("/core/admin/counter/branches", HttpMethod.GET, null, operator);
    }

    public JsonNode createBranch(Object body, CmsPrincipal operator) {
        return exchange("/core/admin/counter/branches", HttpMethod.POST, body, operator);
    }

    public JsonNode listStaff(CmsPrincipal operator) {
        return exchange("/core/admin/counter/staff", HttpMethod.GET, null, operator);
    }

    public JsonNode createStaff(Object body, CmsPrincipal operator) {
        return exchange("/core/admin/counter/staff", HttpMethod.POST, body, operator);
    }

    private JsonNode exchange(String path, HttpMethod method, Object body, CmsPrincipal operator) {
        String url = coreBaseUrl.replaceAll("/+$", "") + path;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Tenant-Id", tenantId);
        headers.set("X-Actor-Id", operator.userId());
        headers.set("X-Actor-Type", "ADMIN");
        headers.set("X-Request-Id", UUID.randomUUID().toString());
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        if (serviceToken != null && !serviceToken.isBlank()) {
            headers.set("X-Service-Token", serviceToken);
        }

        try {
            return restTemplate.exchange(url, method, new HttpEntity<>(body, headers), JsonNode.class).getBody();
        } catch (RestClientResponseException ex) {
            String message = sourceMessage(ex);
            log.warn("Core counter management error: method={}, path={}, status={}, message={}",
                    method, path, ex.getStatusCode(), message);
            throw new SourceServiceException(ex.getStatusCode(), message);
        } catch (Exception ex) {
            log.error("Cannot connect to Core counter management: method={}, path={}", method, path, ex);
            throw new SourceServiceException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "Không thể kết nối với VNFITE Core. Vui lòng thử lại.");
        }
    }

    private String sourceMessage(RestClientResponseException ex) {
        try {
            JsonNode body = objectMapper.readTree(ex.getResponseBodyAsString());
            for (String field : new String[]{"message", "detail", "error"}) {
                String value = body.path(field).asText();
                if (!value.isBlank()) return value;
            }
        } catch (Exception ignored) {
            // Fall through to a status-based message without exposing internal URLs.
        }
        return "VNFITE Core từ chối yêu cầu (" + ex.getStatusCode().value() + ")";
    }
}
