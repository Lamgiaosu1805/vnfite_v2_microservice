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
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * HTTP client gọi TIKLUY-ms002 (mặc định TEST 42.113.122.119:9999; live 42.113.122.155:8888).
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
        h.set("requestId", txnId);
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
    public TransferInitiation fundTransfer(String txnId, String accNo,
                                           String bankCode, String creditAccount, String amount) {
        return fundTransfer(txnId, accNo, bankCode, creditAccount, amount, props.getSource());
    }

    /**
     * Gửi lệnh rút tiền với source tường minh. TIKLUY 8888 đọc source trong JSON body
     * để chọn đúng tài khoản chi/kênh MB: VNFITE -> 6966638888/YFCH;
     * VNFFITE_CAPITAL -> nhánh 6RCH cũ.
     */
    public TransferInitiation fundTransfer(String txnId, String accNo,
                                           String bankCode, String creditAccount, String amount,
                                           String source) {
        Map<String, Object> body = buildFundTransferBody(bankCode, creditAccount, amount, source);
        body.put("accNo", accNo);
        body.put("clientReference", txnId);

        try {
            HttpHeaders headers = authHeaders(txnId);
            addInternalSecretHeader(headers);
            ResponseEntity<JsonNode> resp = restTemplate.exchange(
                    props.getBaseUrl() + "/api/v1/transfer-money",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    JsonNode.class);

            JsonNode data = extractData(resp.getBody(), "fundTransfer", true);
            return parseTransferInitiation(data);

        } catch (Exception e) {
            log.error("txnId={} source={} TIKLUY fundTransfer failed: {}", txnId, source, e.getMessage());
            throw new RuntimeException("Chuyển tiền TIKLUY thất bại: " + e.getMessage(), e);
        }
    }

    static TransferInitiation parseTransferInitiation(JsonNode data) {
        JsonNode transfer = data.path("data");
        String providerReference = transfer.path("transactionId").asText("");
        if (providerReference.isBlank()) {
            throw new RuntimeException(
                    "fundTransfer: TIKLUY không trả mã giao dịch YFCH để đối soát");
        }
        String rawStatus = transfer.path("status").asText("")
                .trim().toUpperCase(Locale.ROOT);
        String ftNumber = transfer.path("ftNumber").asText("");
        String errorCode = data.path("errorCode").asText("");
        TransferState state = parseState(rawStatus, errorCode);
        return new TransferInitiation(
                providerReference, state, rawStatus, ftNumber, errorCode);
    }

    static Map<String, Object> buildFundTransferBody(
            String bankCode, String creditAccount, String amount, String source) {
        if (source == null || source.isBlank()) {
            throw new IllegalStateException(
                    "Thiếu source chuyển tiền TIKLUY; từ chối gửi để tránh chọn sai tài khoản chi.");
        }
        String normalizedSource = source.trim().toUpperCase();
        if (!"VNFITE".equals(normalizedSource)
                && !"VNFFITE_CAPITAL".equals(normalizedSource)) {
            throw new IllegalStateException(
                    "Source chuyển tiền TIKLUY không hợp lệ: " + source);
        }
        Map<String, Object> body = new HashMap<>();
        body.put("bankCode", bankCode);
        body.put("creditResourceNumber", creditAccount);
        body.put("transferAmount", normalizeVndAmount(amount));
        body.put("source", normalizedSource);
        return body;
    }

    static String normalizeVndAmount(String amount) {
        if (amount == null || amount.isBlank()) {
            throw new IllegalArgumentException("Số tiền chuyển không được để trống");
        }
        try {
            BigDecimal parsed = new BigDecimal(amount.trim());
            if (parsed.signum() <= 0) {
                throw new IllegalArgumentException("Số tiền chuyển phải lớn hơn 0");
            }
            return parsed.setScale(0, RoundingMode.UNNECESSARY).toPlainString();
        } catch (NumberFormatException | ArithmeticException ex) {
            throw new IllegalArgumentException("Số tiền VND phải là số nguyên", ex);
        }
    }

    /**
     * Query trạng thái cuối của mã YFCH tại MB qua TIKLUY. Chỉ trạng thái terminal
     * mới làm payment-service hoàn tất hoặc hoàn khóa; trạng thái lạ giữ PROCESSING.
     */
    public TransferQueryResult getTransferStatus(String txnId, String providerReference) {
        try {
            String url = props.getBaseUrl() + "/api/v1/get-transaction?transactionCode="
                    + providerReference;
            ResponseEntity<JsonNode> resp = restTemplate.exchange(
                    url, HttpMethod.GET,
                    new HttpEntity<>(authHeaders(txnId)),
                    JsonNode.class);

            JsonNode mbResponse = extractData(resp.getBody(), "getTransferStatus", true);
            return parseTransferStatus(mbResponse);
        } catch (Exception e) {
            log.error("txnId={} providerRef={} TIKLUY getTransferStatus failed: {}",
                    txnId, providerReference, e.getMessage());
            throw new RuntimeException("Đối soát trạng thái chuyển tiền TIKLUY thất bại: "
                    + e.getMessage(), e);
        }
    }

    static TransferQueryResult parseTransferStatus(JsonNode mbResponse) {
        String errorCode = mbResponse.path("errorCode").asText("");
        JsonNode detail = mbResponse.path("data");
        String rawStatus = detail.path("transStatus").asText("")
                .trim().toUpperCase(Locale.ROOT);
        String ftNumber = detail.path("ft").asText("");

        TransferState state = parseState(rawStatus, errorCode);
        return new TransferQueryResult(state, rawStatus, ftNumber, errorCode);
    }

    private static TransferState parseState(String rawStatus, String errorCode) {
        if ("SUCCESS".equals(rawStatus) || "COMPLETED".equals(rawStatus)) {
            return TransferState.SUCCESS;
        } else if ("FAILED".equals(rawStatus)
                || "FAIL".equals(rawStatus)
                || "REJECTED".equals(rawStatus)
                || "CANCELLED".equals(rawStatus)
                || "ERROR".equals(rawStatus)) {
            return TransferState.FAILED;
        } else if (!errorCode.isBlank() && !"000".equals(errorCode)) {
            return TransferState.FAILED;
        }
        return TransferState.PROCESSING;
    }

    /**
     * Trừ số dư VA sau khi MB xác nhận chuyển thành công. Endpoint này là additive
     * và idempotent theo txnId ở TIKLUY; gọi lại không được trừ tiền lần hai.
     */
    public void settleWithdrawal(String txnId, String accNo, BigDecimal amount) {
        Map<String, Object> body = Map.of(
                "amount", amount,
                "plus", false,
                "content", "Rút tiền khỏi ví VNFITE");
        HttpHeaders headers = authHeaders(txnId);
        headers.set("source", "VNFITE");
        addInternalSecretHeader(headers);
        try {
            ResponseEntity<JsonNode> resp = restTemplate.exchange(
                    props.getBaseUrl() + "/api/v2/account/" + accNo + "/balance-adjustment",
                    HttpMethod.PUT,
                    new HttpEntity<>(body, headers),
                    JsonNode.class);
            extractData(resp.getBody(), "settleWithdrawal", true);
            log.info("txnId={} TIKLUY settled withdrawal accNo={} amount={}",
                    txnId, accNo, amount);
        } catch (Exception e) {
            log.error("txnId={} TIKLUY settle withdrawal failed accNo={}: {}",
                    txnId, accNo, e.getMessage());
            throw new RuntimeException("Không thể quyết toán số dư rút tiền tại TIKLUY: "
                    + e.getMessage(), e);
        }
    }

    /**
     * Cộng tiền trực tiếp vào tài khoản TIKLUY — dùng cho môi trường test khi không có giao dịch MB Bank thật.
     * Lỗi chỉ log, không ném (nạp tiền không nên chặn flow).
     */
    public void topUpAccount(String txnId, String accNo, BigDecimal amount) {
        adjustBalance(txnId, accNo, amount, true, false);
    }

    /**
     * Trừ tiền trực tiếp khỏi tài khoản TIKLUY — dùng khi giải ngân (tiền rời ví nhà đầu tư).
     * Ném RuntimeException nếu thất bại để caller rollback (không được nuốt lỗi tiền).
     */
    public void deductAccount(String txnId, String accNo, BigDecimal amount) {
        adjustBalance(txnId, accNo, amount, false, true);
    }

    /** Điều chỉnh VA qua endpoint additive, idempotent theo txnId của hệ thống mới. */
    private void adjustBalance(String txnId, String accNo, BigDecimal amount, boolean plus, boolean throwOnError) {
        Map<String, Object> body = Map.of(
                "amount", amount,
                "plus", plus,
                "content", plus ? "Cộng tiền ví VNFITE" : "Trừ tiền ví VNFITE");
        HttpHeaders headers = authHeaders(txnId);
        headers.set("source", "VNFITE");
        addInternalSecretHeader(headers);
        try {
            ResponseEntity<JsonNode> resp = restTemplate.exchange(
                    props.getBaseUrl() + "/api/v2/account/" + accNo + "/balance-adjustment",
                    HttpMethod.PUT,
                    new HttpEntity<>(body, headers),
                    JsonNode.class);
            extractData(resp.getBody(), "adjustBalance", true);
            log.info("txnId={} TIKLUY adjust accNo={} amount={} plus={}", txnId, accNo, amount, plus);
        } catch (Exception e) {
            log.warn("txnId={} TIKLUY adjust failed accNo={} plus={}: {}", txnId, accNo, plus, e.getMessage());
            if (throwOnError) {
                throw new RuntimeException("Cập nhật số dư TIKLUY thất bại: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Xác minh tên chủ tài khoản ngân hàng thực (qua MB Bank API).
     */
    public String verifyBankAccount(String txnId, String bankCode, String bankAccountNo) {
        try {
            String url = props.getBaseUrl()
                    + "/api/v1/account-bank?bankCode=" + bankCode
                    + "&accountNumber=" + bankAccountNo;

            ResponseEntity<JsonNode> resp = restTemplate.exchange(
                    url, HttpMethod.GET,
                    new HttpEntity<>(authHeaders(txnId)),
                    JsonNode.class);

            // TIKLUY /api/v1/account-bank: body.data.data.accountName (double-nested)
            JsonNode outer = extractData(resp.getBody(), "verifyBankAccount");
            JsonNode inner = outer.has("data") && !outer.get("data").isNull()
                    ? outer.get("data") : outer;
            return inner.has("accountName") ? inner.get("accountName").asText() : "";

        } catch (Exception e) {
            log.error("txnId={} TIKLUY verifyBankAccount failed: {}", txnId, e.getMessage());
            throw new RuntimeException("Xác minh tài khoản ngân hàng thất bại: " + e.getMessage(), e);
        }
    }

    /** Thêm X-VNFITE-Internal-Secret vào header nếu đã cấu hình; TIKLUY 8888 yêu cầu header này. */
    private void addInternalSecretHeader(HttpHeaders headers) {
        String secret = props.getInternalSecret();
        if (secret != null && !secret.isBlank()) {
            headers.set("X-VNFITE-Internal-Secret", secret);
        }
    }

    private JsonNode extractData(JsonNode body, String op) {
        return extractData(body, op, false);
    }

    private JsonNode extractData(JsonNode body, String op, boolean requireSuccess) {
        if (body == null) throw new RuntimeException(op + ": empty response");
        // TIKLUY BaseResponse: { result: {isOK, responseCode, responseMessage}, data: {...} }
        JsonNode result = body.path("result");
        if (requireSuccess && !result.isMissingNode()) {
            boolean hasOk = result.has("isOK") || result.has("ok");
            boolean ok = result.has("isOK")
                    ? result.path("isOK").asBoolean()
                    : result.path("ok").asBoolean();
            if (hasOk && !ok) {
                String code = result.path("responseCode").asText("TIKLUY_ERROR");
                String message = result.path("responseMessage").asText("TIKLUY từ chối yêu cầu");
                throw new TikluyBusinessException(code, message);
            }
        }
        if (body.has("data") && !body.get("data").isNull()) {
            return body.get("data");
        }
        if (requireSuccess) {
            throw new RuntimeException(op + ": response không có data");
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

    public enum TransferState {
        PROCESSING,
        SUCCESS,
        FAILED
    }

    public record TransferQueryResult(
            TransferState state,
            String rawStatus,
            String ftNumber,
            String errorCode) {
    }

    public record TransferInitiation(
            String providerReference,
            TransferState state,
            String rawStatus,
            String ftNumber,
            String errorCode) {
    }

    public static class TikluyBusinessException extends RuntimeException {
        private final String errorCode;

        public TikluyBusinessException(String errorCode, String message) {
            super(errorCode + ": " + message);
            this.errorCode = errorCode;
        }

        public String getErrorCode() {
            return errorCode;
        }
    }
}
