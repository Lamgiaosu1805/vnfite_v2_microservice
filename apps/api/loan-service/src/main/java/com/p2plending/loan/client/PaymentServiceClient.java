package com.p2plending.loan.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2plending.loan.dto.response.WalletBalanceResponse;
import com.p2plending.loan.exception.InvalidLoanStateException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;

/**
 * Gọi payment-service ({@code /internal/payment/wallet/**}) để kiểm tra & khóa/trừ tiền ví
 * trong luồng đầu tư. Bảo vệ bằng header {@code X-Internal-Secret}.
 *
 * <p>Khác với {@link AuthServiceClient} (fail-silent cho dữ liệu hiển thị), các thao tác tiền ở
 * đây <b>fail-closed</b>: mọi lỗi đều ném {@link InvalidLoanStateException} (→ 409 ở
 * GlobalExceptionHandler) để chặn đầu tư khi không xác minh được số dư. Message lỗi của
 * payment-service được bóc theo thứ tự {@code details[] → message → detail → error}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.payment-service.base-url:http://payment-service:8086}")
    private String paymentBaseUrl;

    @Value("${app.internal.secret}")
    private String internalSecret;

    /** Số dư khả dụng của ví nhà đầu tư. Lỗi/không kết nối được → ném InvalidLoanStateException. */
    public BigDecimal getAvailableBalance(String userId) {
        try {
            ResponseEntity<WalletBalanceResponse> resp = restTemplate.exchange(
                    paymentBaseUrl + "/internal/payment/wallet/" + userId,
                    HttpMethod.GET,
                    new HttpEntity<>(authHeaders()),
                    WalletBalanceResponse.class);
            WalletBalanceResponse body = resp.getBody();
            if (body == null || body.getAvailableBalance() == null) {
                throw new InvalidLoanStateException("Không lấy được số dư ví. Vui lòng thử lại.");
            }
            return body.getAvailableBalance();
        } catch (HttpStatusCodeException e) {
            throw new InvalidLoanStateException(extractMessage(e, "Không lấy được số dư ví."));
        } catch (ResourceAccessException e) {
            log.error("payment-service unreachable (wallet {}): {}", userId, e.getMessage());
            throw new InvalidLoanStateException("Không thể kết nối ví đầu tư. Vui lòng thử lại.");
        }
    }

    /** Khóa tiền khi nhà đầu tư ký hợp đồng đầu tư (cam kết vốn). */
    public void lock(String userId, BigDecimal amount, String description) {
        post(userId, "lock", amount, description, null, "Không thể khóa tiền đầu tư.");
    }

    public void lock(String userId, BigDecimal amount, String description, String referenceId) {
        post(userId, "lock", amount, description, referenceId, "Không thể khóa tiền đầu tư.");
    }

    /**
     * Mở khóa tiền khi lệnh đầu tư bị hủy/hết hạn (hoàn về số dư khả dụng).
     * referenceId (vd "REFUND-{offerId}") để payment-service idempotent, chống hoàn trùng.
     */
    public void unlock(String userId, BigDecimal amount, String description, String referenceId) {
        post(userId, "unlock", amount, description, referenceId, "Không thể hoàn tiền đầu tư.");
    }

    /**
     * Trừ tiền đã khóa khi khoản gọi vốn được giải ngân (locked → debit thật).
     * referenceId (vd "DISBURSE-{offerId}") để payment-service idempotent, chống debit trùng khi retry.
     */
    public void debit(String userId, BigDecimal amount, String description, String referenceId) {
        post(userId, "debit", amount, description, referenceId, "Không thể trừ tiền giải ngân.");
    }

    /**
     * Trừ ví người gọi vốn khi trả nợ (vế TRỪ của chuyển nội bộ borrower → investors).
     * Fail-closed: số dư không đủ → payment-service trả 422 → ném InvalidLoanStateException (→ 409).
     * referenceId (vd "REPAY-OUT-{loanId}-P{n}-{date}") để idempotent, chống trừ trùng khi retry.
     */
    public void debitRepayment(String userId, BigDecimal amount, String description, String referenceId) {
        post(userId, "repay-debit", amount, description, referenceId, "Không thể trừ tiền trả nợ từ ví.");
    }

    /**
     * Cộng ví nhà đầu tư khi nhận hoàn trả (vế CỘNG của chuyển nội bộ).
     * referenceId (vd "REPAY-IN-{...}-{offerId}") để idempotent, chống cộng trùng khi retry.
     */
    public void creditRepayment(String userId, BigDecimal amount, String description, String referenceId) {
        post(userId, "credit", amount, description, referenceId, "Không thể cộng tiền hoàn trả vào ví.");
    }

    /**
     * Cộng ví VNF người gọi vốn khi giải ngân (tổng tiền từ các nhà đầu tư → ví người gọi vốn).
     * referenceId (vd "CREDIT-BORROWER-{loanId}") để idempotent, chống cộng trùng khi retry giải ngân.
     */
    public void creditBorrower(String userId, BigDecimal amount, String description, String referenceId) {
        post(userId, "credit-disbursement", amount, description, referenceId, "Không thể cộng tiền giải ngân vào ví người gọi vốn.");
    }

    private void post(String userId, String action, BigDecimal amount, String description,
                      String referenceId, String fallback) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(paymentBaseUrl + "/internal/payment/wallet/" + userId + "/" + action)
                .queryParam("amount", amount.toPlainString())
                .queryParam("description", description);
        if (referenceId != null) {
            builder.queryParam("referenceId", referenceId);
        }
        URI uri = builder.build().encode().toUri();
        try {
            restTemplate.exchange(uri, HttpMethod.POST, new HttpEntity<>(authHeaders()), Void.class);
        } catch (HttpStatusCodeException e) {
            throw new InvalidLoanStateException(extractMessage(e, fallback));
        } catch (ResourceAccessException e) {
            log.error("payment-service unreachable ({} wallet {}): {}", action, userId, e.getMessage());
            throw new InvalidLoanStateException("Không thể kết nối ví đầu tư. Vui lòng thử lại.");
        }
    }

    private HttpHeaders authHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Internal-Secret", internalSecret);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    /** Bóc message lỗi từ payment-service theo thứ tự details[] → message → detail → error. */
    private String extractMessage(HttpStatusCodeException e, String fallback) {
        try {
            JsonNode body = objectMapper.readTree(e.getResponseBodyAsString());
            JsonNode details = body.get("details");
            if (details != null && details.isArray() && details.size() > 0) {
                return details.get(0).asText();
            }
            for (String key : new String[]{"message", "detail", "error"}) {
                if (body.hasNonNull(key) && !body.get(key).asText().isBlank()) {
                    return body.get(key).asText();
                }
            }
        } catch (Exception ignored) {
            log.warn("Cannot parse payment-service error body: {}", e.getResponseBodyAsString());
        }
        return fallback;
    }
}
