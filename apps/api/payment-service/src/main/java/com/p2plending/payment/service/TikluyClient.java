package com.p2plending.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2plending.payment.config.TikluyProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * HTTP client gọi TIKLUY-ms002 (42.113.122.155:8888).
 * Tự động cache JWT token và refresh khi hết hạn.
 *
 * Xác thực với TIKLUY: Basic auth (clientId:clientSecret) → POST /api/v1/token-generate → JWT
 * Sau đó dùng JWT trong header Authorization cho các call tiếp theo.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TikluyClient {

    private final TikluyProperties props;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final AtomicReference<String> cachedToken = new AtomicReference<>();
    private volatile long tokenExpiresAt = 0;

    // ─── Token management ─────────────────────────────────────────────────────

    private String getToken() {
        long now = Instant.now().getEpochSecond();
        if (cachedToken.get() != null && now < tokenExpiresAt - 30) {
            return cachedToken.get();
        }
        return refreshToken();
    }

    private synchronized String refreshToken() {
        try {
            String credentials = props.getClientId() + ":" + props.getClientSecret();
            String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Basic " + encoded);
            // TIKLUY AuthController: GET /auth/token — Basic auth → trả BaseResponse{data:{token}}
            // JWT_TOKEN_VALIDITY = 5 * 60 = 300s (xem JwtTokenUtil.java trong TIKLUY)
            ResponseEntity<JsonNode> resp = restTemplate.exchange(
                    props.getBaseUrl() + "/auth/token",
                    HttpMethod.GET,
                    new HttpEntity<>(null, headers),
                    JsonNode.class);

            JsonNode body = resp.getBody();
            // TIKLUY BaseResponse: { result: {isOK, responseCode, responseMessage}, data: { accessToken: "..." } }
            if (body == null || !body.path("data").has("accessToken")) {
                throw new RuntimeException("TIKLUY /auth/token returned unexpected response: " + body);
            }

            String token = body.path("data").path("accessToken").asText();
            // TIKLUY JWT TTL = 300s, cache với buffer 30s
            cachedToken.set(token);
            tokenExpiresAt = Instant.now().getEpochSecond() + 300;
            log.debug("TIKLUY token refreshed");
            return token;

        } catch (Exception e) {
            log.error("Failed to get TIKLUY token: {}", e.getMessage());
            throw new RuntimeException("Cannot authenticate with TIKLUY: " + e.getMessage(), e);
        }
    }

    private HttpHeaders authHeaders(String txnId) {
        HttpHeaders h = new HttpHeaders();
        h.set("Authorization", "Bearer " + getToken());
        h.set("transactionId", txnId);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    // ─── Account (Wallet VA) ──────────────────────────────────────────────────

    /**
     * Tạo virtual account (VA) cho user.
     * @return accNo vd "VNF0000000001"
     */
    public String createAccount(String txnId, String fullName, String identityNumber) {
        Map<String, String> body = Map.of(
                "name", fullName,
                "identityNumber", identityNumber
        );

        HttpHeaders headers = authHeaders(txnId);
        headers.set("source", props.getSource());

        try {
            ResponseEntity<JsonNode> resp = restTemplate.exchange(
                    props.getBaseUrl() + "/api/v2/account",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    JsonNode.class);

            JsonNode data = extractData(resp.getBody(), "createAccount");
            return data.get("accNo").asText();

        } catch (Exception e) {
            log.error("txnId={} TIKLUY createAccount failed: {}", txnId, e.getMessage());
            throw new RuntimeException("Tạo tài khoản TIKLUY thất bại: " + e.getMessage(), e);
        }
    }

    /**
     * Lấy thông tin số dư tài khoản từ TIKLUY.
     */
    public TikluyAccountInfo getAccount(String txnId, String accNo) {
        try {
            ResponseEntity<JsonNode> resp = restTemplate.exchange(
                    props.getBaseUrl() + "/api/v2/account?customerAcc=" + accNo,
                    HttpMethod.GET,
                    new HttpEntity<>(authHeaders(txnId)),
                    JsonNode.class);

            JsonNode data = extractData(resp.getBody(), "getAccount");
            return TikluyAccountInfo.builder()
                    .accNo(data.path("accNo").asText())
                    .accName(data.path("accName").asText())
                    .totalMoney(new BigDecimal(data.path("totalMoney").asText("0")))
                    .lockedMoney(new BigDecimal(data.path("lockedMoney").asText("0")))
                    .build();

        } catch (Exception e) {
            log.error("txnId={} TIKLUY getAccount({}) failed: {}", txnId, accNo, e.getMessage());
            throw new RuntimeException("Lấy thông tin tài khoản TIKLUY thất bại", e);
        }
    }

    /**
     * Yêu cầu TIKLUY chuyển tiền từ TK công ty ra ngân hàng của user.
     * @return transactionId TIKLUY phản hồi (để track trạng thái)
     */
    public String fundTransfer(String txnId, String accNo,
                               String bankCode, String creditAccount, String amount) {
        Map<String, Object> fundTransferReq = Map.of(
                "bankCode", bankCode,
                "creditAccount", creditAccount,
                "transferAmount", amount
        );
        Map<String, Object> body = Map.of("fundTransferRequest", fundTransferReq);

        try {
            ResponseEntity<JsonNode> resp = restTemplate.exchange(
                    props.getBaseUrl() + "/api/v2/account/" + accNo,
                    HttpMethod.PUT,
                    new HttpEntity<>(body, authHeaders(txnId)),
                    JsonNode.class);

            JsonNode data = extractData(resp.getBody(), "fundTransfer");
            // TIKLUY trả về object chứa transactionId trong response
            if (data.has("transactionId")) {
                return data.get("transactionId").asText();
            }
            return txnId; // fallback

        } catch (Exception e) {
            log.error("txnId={} TIKLUY fundTransfer failed: {}", txnId, e.getMessage());
            throw new RuntimeException("Chuyển tiền TIKLUY thất bại: " + e.getMessage(), e);
        }
    }

    /**
     * Xác minh tên chủ tài khoản ngân hàng thực (qua MB Bank API).
     */
    public String verifyBankAccount(String txnId, String bankCode, String bankAccountNo) {
        try {
            String url = props.getBaseUrl()
                    + "/api/v1/account-bank?bankCode=" + bankCode
                    + "&bankAccountNumber=" + bankAccountNo;

            ResponseEntity<JsonNode> resp = restTemplate.exchange(
                    url, HttpMethod.GET,
                    new HttpEntity<>(authHeaders(txnId)),
                    JsonNode.class);

            JsonNode data = extractData(resp.getBody(), "verifyBankAccount");
            return data.has("accountName") ? data.get("accountName").asText() : "";

        } catch (Exception e) {
            log.error("txnId={} TIKLUY verifyBankAccount failed: {}", txnId, e.getMessage());
            throw new RuntimeException("Xác minh tài khoản ngân hàng thất bại: " + e.getMessage(), e);
        }
    }

    private JsonNode extractData(JsonNode body, String op) {
        if (body == null) throw new RuntimeException(op + ": empty response");
        // TIKLUY BaseResponse: { result: {isOK, responseCode, responseMessage}, data: {...} }
        if (body.has("data") && !body.get("data").isNull()) {
            return body.get("data");
        }
        return body;
    }

    // ─── Inner DTOs ───────────────────────────────────────────────────────────

    @lombok.Builder
    @lombok.Data
    public static class TikluyAccountInfo {
        private String accNo;
        private String accName;
        private BigDecimal totalMoney;
        private BigDecimal lockedMoney;
    }
}
