package com.p2plending.cms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2plending.cms.domain.enums.UserAccountStatus;
import com.p2plending.cms.dto.request.LoanActionRequest;
import com.p2plending.cms.dto.response.LoanSummaryResponse;
import com.p2plending.cms.dto.response.PagedResponse;
import com.p2plending.cms.dto.response.UserSummaryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SourceServiceClient {

    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${cms.sources.auth-url:http://auth-service:8081}")
    private String authServiceUrl;

    @Value("${cms.sources.loan-url:http://loan-service:8082}")
    private String loanServiceUrl;

    @Value("${cms.sources.internal-secret:dev-internal-secret}")
    private String internalSecret;

    public PagedResponse<UserSummaryResponse> getUsers(
            String kycStatus, String role, UserAccountStatus status, String search, int page, int size) {
        String url = UriComponentsBuilder.fromHttpUrl(authServiceUrl)
                .path("/internal/users")
                .queryParamIfPresent("kycStatus", Optional.ofNullable(kycStatus))
                .queryParamIfPresent("role", Optional.ofNullable(role))
                .queryParamIfPresent("search", Optional.ofNullable(search))
                .queryParam("page", page)
                .queryParam("size", size)
                .toUriString();

        JsonNode root = exchangeForJson(url, HttpMethod.GET, null);
        PagedResponse<UserSummaryResponse> response = parseUserPage(root);
        if (status != null) {
            List<UserSummaryResponse> filtered = response.getContent().stream()
                    .filter(user -> status.equals(user.getAccountStatus()))
                    .toList();
            response.setContent(filtered);
            response.setTotalElements(filtered.size());
            response.setTotalPages(filtered.isEmpty() ? 0 : 1);
            response.setLast(true);
        }
        return response;
    }

    public UserSummaryResponse getUser(String userId) {
        String url = UriComponentsBuilder.fromHttpUrl(authServiceUrl)
                .path("/internal/users/{userId}")
                .buildAndExpand(userId)
                .toUriString();
        return parseUser(exchangeForJson(url, HttpMethod.GET, null));
    }

    // ─── Stats ────────────────────────────────────────────────────────────────

    public com.fasterxml.jackson.databind.JsonNode getUserStats(java.time.LocalDate from) {
        String url = UriComponentsBuilder.fromHttpUrl(authServiceUrl)
                .path("/internal/users/stats")
                .queryParam("from", from.toString())
                .toUriString();
        return exchangeForJson(url, HttpMethod.GET, null);
    }

    public com.fasterxml.jackson.databind.JsonNode getLoanStats(java.time.LocalDate from) {
        String url = UriComponentsBuilder.fromHttpUrl(loanServiceUrl)
                .path("/internal/loans/stats")
                .queryParam("from", from.toString())
                .toUriString();
        return exchangeForJson(url, HttpMethod.GET, null);
    }

    /** Gợi ý hỗ trợ thẩm định — passthrough JSON nguyên bản từ loan-service. */
    public JsonNode getAppraisalSuggestion(String loanId, boolean discouraged) {
        String url = UriComponentsBuilder.fromHttpUrl(loanServiceUrl)
                .path("/internal/loans/{loanId}/appraisal-suggestion")
                .queryParam("discouraged", discouraged)
                .buildAndExpand(loanId)
                .toUriString();
        return exchangeForJson(url, HttpMethod.GET, null);
    }

    /** Lịch trả nợ của một khoản — passthrough JSON nguyên bản từ loan-service. */
    public JsonNode getRepaymentSchedule(String loanId) {
        String url = UriComponentsBuilder.fromHttpUrl(loanServiceUrl)
                .path("/internal/loans/{loanId}/repayments")
                .buildAndExpand(loanId)
                .toUriString();
        return exchangeForJson(url, HttpMethod.GET, null);
    }

    public LoanSummaryResponse getLoanById(String loanId) {
        String url = UriComponentsBuilder.fromHttpUrl(loanServiceUrl)
                .path("/internal/loans/{loanId}")
                .buildAndExpand(loanId)
                .toUriString();
        return parseLoan(exchangeForJson(url, HttpMethod.GET, null));
    }

    public PagedResponse<LoanSummaryResponse> getLoans(String status, String borrowerId,
                                                       String province, String search, int page, int size) {
        URI uri = UriComponentsBuilder.fromHttpUrl(loanServiceUrl)
                .path("/internal/loans")
                .queryParamIfPresent("status",     Optional.ofNullable(status))
                .queryParamIfPresent("borrowerId", Optional.ofNullable(borrowerId))
                .queryParamIfPresent("province",   Optional.ofNullable(province))
                .queryParamIfPresent("search",     Optional.ofNullable(search))
                .queryParam("page", page)
                .queryParam("size", size)
                .queryParam("sortBy", "createdAt")
                .queryParam("sortDir", "desc")
                .build()
                .encode()
                .toUri();
        return parseLoanPage(exchangeForJson(uri, HttpMethod.GET, null));
    }

    public LoanSummaryResponse proposeLoan(String loanId, BigDecimal proposedAmount,
                                           BigDecimal proposedInterestRate, String note, String proposedBy) {
        String url = UriComponentsBuilder.fromHttpUrl(loanServiceUrl)
                .path("/internal/loans/{loanId}/propose")
                .buildAndExpand(loanId)
                .toUriString();
        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("proposedAmount", proposedAmount);
        body.put("proposedInterestRate", proposedInterestRate);
        body.put("note", note);
        body.put("proposedBy", proposedBy);
        return parseLoan(exchangeForJson(url, HttpMethod.PUT, body));
    }

    public LoanSummaryResponse approveLoan(String loanId, LoanActionRequest request, String reviewedBy) {
        return reviewLoan(loanId, "approve", request, reviewedBy);
    }

    public LoanSummaryResponse rejectLoan(String loanId, LoanActionRequest request, String reviewedBy) {
        return reviewLoan(loanId, "reject", request, reviewedBy);
    }

    private LoanSummaryResponse reviewLoan(String loanId, String action, LoanActionRequest request, String reviewedBy) {
        String url = UriComponentsBuilder.fromHttpUrl(loanServiceUrl)
                .path("/internal/loans/{loanId}/{action}")
                .buildAndExpand(loanId, action)
                .toUriString();
        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("interestRate", request.getInterestRate());
        body.put("reason", request.getReason());
        body.put("reviewedBy", reviewedBy);
        return parseLoan(exchangeForJson(url, HttpMethod.PUT, body));
    }

    private JsonNode exchangeForJson(String url, HttpMethod method, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(INTERNAL_SECRET_HEADER, internalSecret);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Object> entity = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.exchange(url, method, entity, String.class);
        try {
            return objectMapper.readTree(response.getBody());
        } catch (Exception ex) {
            log.error("Failed to parse source service response from {}", url, ex);
            throw new IllegalStateException("Không đọc được phản hồi từ service nguồn");
        }
    }

    private JsonNode exchangeForJson(URI uri, HttpMethod method, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(INTERNAL_SECRET_HEADER, internalSecret);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Object> entity = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.exchange(uri, method, entity, String.class);
        try {
            return objectMapper.readTree(response.getBody());
        } catch (Exception ex) {
            log.error("Failed to parse source service response from {}", uri, ex);
            throw new IllegalStateException("Không đọc được phản hồi từ service nguồn");
        }
    }

    private PagedResponse<UserSummaryResponse> parseUserPage(JsonNode pageNode) {
        List<UserSummaryResponse> content = new ArrayList<>();
        pageNode.path("content").forEach(node -> content.add(parseUser(node)));
        return PagedResponse.<UserSummaryResponse>builder()
                .content(content)
                .page(pageNode.path("page").asInt())
                .size(pageNode.path("size").asInt())
                .totalElements(pageNode.path("totalElements").asLong())
                .totalPages(pageNode.path("totalPages").asInt())
                .last(pageNode.path("last").asBoolean())
                .build();
    }

    private UserSummaryResponse parseUser(JsonNode node) {
        return UserSummaryResponse.builder()
                .userId(text(node, "userId"))
                .email(text(node, "email"))
                .fullName(text(node, "fullName"))
                .cccdNumber(text(node, "cccdNumber"))
                .phone(text(node, "phone"))
                .role(text(node, "role"))
                .kycStatus(text(node, "kycStatus"))
                .accountStatus(parseAccountStatus(text(node, "accountStatus")))
                .createdAt(dateTime(node, "createdAt"))
                .build();
    }

    private PagedResponse<LoanSummaryResponse> parseLoanPage(JsonNode pageNode) {
        List<LoanSummaryResponse> content = new ArrayList<>();
        pageNode.path("content").forEach(node -> content.add(parseLoan(node)));
        return PagedResponse.<LoanSummaryResponse>builder()
                .content(content)
                .page(pageNode.path("page").asInt())
                .size(pageNode.path("size").asInt())
                .totalElements(pageNode.path("totalElements").asLong())
                .totalPages(pageNode.path("totalPages").asInt())
                .last(pageNode.path("last").asBoolean())
                .build();
    }

    private LoanSummaryResponse parseLoan(JsonNode node) {
        String borrowerId = text(node, "borrowerId");
        return LoanSummaryResponse.builder()
                .loanId(text(node, "id"))
                .loanCode(text(node, "loanCode"))
                .borrowerId(borrowerId)
                .borrowerName(resolveBorrowerName(borrowerId))
                .productName(text(node, "productName"))
                .amount(decimal(node, "amount"))
                .interestRate(decimal(node, "interestRate"))
                .proposedAmount(decimal(node, "proposedAmount"))
                .proposedInterestRate(decimal(node, "proposedInterestRate"))
                .proposedBy(text(node, "proposedBy"))
                .proposedAt(dateTime(node, "proposedAt"))
                .appraisalNote(text(node, "appraisalNote"))
                .termMonths(node.hasNonNull("termMonths") ? node.get("termMonths").asInt() : null)
                .purpose(text(node, "purpose"))
                .occupation(text(node, "occupation"))
                .workplace(text(node, "workplace"))
                .monthlyIncome(decimal(node, "monthlyIncome"))
                .currentAddress(text(node, "currentAddress"))
                .commune(text(node, "commune"))
                .province(text(node, "province"))
                .referredBy(text(node, "referredBy"))
                .status(text(node, "status"))
                .rejectionReason(text(node, "rejectionReason"))
                .reviewedAt(dateTime(node, "reviewedAt"))
                .reviewedBy(text(node, "reviewedBy"))
                .createdAt(dateTime(node, "createdAt"))
                .build();
    }

    private String resolveBorrowerName(String borrowerId) {
        if (borrowerId == null) return null;
        try {
            UserSummaryResponse user = getUser(borrowerId);
            return user.getFullName() != null ? user.getFullName() : user.getPhone();
        } catch (Exception ex) {
            log.warn("Could not resolve borrower name for {}", borrowerId);
            return null;
        }
    }

    private UserAccountStatus parseAccountStatus(String value) {
        if (value == null) return null;
        return UserAccountStatus.valueOf(value);
    }

    private String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }

    private BigDecimal decimal(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).decimalValue() : null;
    }

    private LocalDateTime dateTime(JsonNode node, String field) {
        return node.hasNonNull(field) ? LocalDateTime.parse(node.get(field).asText()) : null;
    }
}
