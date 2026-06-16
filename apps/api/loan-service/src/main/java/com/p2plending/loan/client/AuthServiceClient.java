package com.p2plending.loan.client;

import com.p2plending.loan.dto.response.InternalUserResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuthServiceClient {

    private final RestTemplate restTemplate;

    @Value("${app.auth-service.base-url:http://auth-service:8081}")
    private String authServiceBaseUrl;

    @Value("${app.internal.secret}")
    private String internalSecret;

    public Optional<InternalUserResponse> getUserById(String userId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Secret", internalSecret);
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<InternalUserResponse> resp = restTemplate.exchange(
                    authServiceBaseUrl + "/internal/users/" + userId,
                    HttpMethod.GET,
                    entity,
                    InternalUserResponse.class
            );
            return Optional.ofNullable(resp.getBody());
        } catch (Exception e) {
            log.warn("Could not fetch user info from auth-service for userId={}: {}", userId, e.getMessage());
            return Optional.empty();
        }
    }
}
