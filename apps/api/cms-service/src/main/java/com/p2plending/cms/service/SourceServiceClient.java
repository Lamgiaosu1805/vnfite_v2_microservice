package com.p2plending.cms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2plending.cms.domain.enums.UserAccountStatus;
import com.p2plending.cms.dto.request.LoanActionRequest;
import com.p2plending.cms.dto.response.CustomerDetailResponse;
import com.p2plending.cms.dto.response.LoanSummaryResponse;
import com.p2plending.cms.dto.response.PagedResponse;
import com.p2plending.cms.dto.response.SystemTransactionSummaryResponse;
import com.p2plending.cms.dto.response.UserSummaryResponse;
import com.p2plending.cms.dto.response.WalletSummaryResponse;
import com.p2plending.cms.dto.response.WalletTransactionSummaryResponse;
import com.p2plending.cms.dto.response.WithdrawalSummaryResponse;
import com.p2plending.cms.exception.SourceServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class SourceServiceClient {

    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";

    /**
     * Lenient formatter — accepts ISO-8601 local datetimes with or without fractional seconds.
     * Covers: "2024-06-05T10:30:00", "2024-06-05T10:30:00.123", "2024-06-05T10:30:00.123456"
     */
    private static final DateTimeFormatter LENIENT_DT = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd'T'HH:mm:ss")
            .optionalStart()
            .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
            .optionalEnd()
            .toFormatter();

    private final RestTemplate restTemplate;
    private final RestTemplate aiRestTemplate;
    private final ObjectMapper objectMapper;

    public SourceServiceClient(RestTemplate restTemplate,
                               @Qualifier("aiRestTemplate") RestTemplate aiRestTemplate,
                               ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.aiRestTemplate = aiRestTemplate;
        this.objectMapper = objectMapper;
    }

    @Value("${cms.sources.auth-url:http://auth-service:8081}")
    private String authServiceUrl;

    @Value("${cms.sources.loan-url:http://loan-service:8082}")
    private String loanServiceUrl;

    @Value("${cms.sources.payment-url:http://payment-service:8086}")
    private String paymentServiceUrl;

    @Value("${cms.sources.credit-url:http://credit-service:8087}")
    private String creditServiceUrl;

    @Value("${cms.sources.internal-secret}")
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

    public CustomerDetailResponse getCustomerDetail(String userId, int transactionPage, int transactionSize,
                                                    int loanPage, int loanSize) {
        UserSummaryResponse profile = getUser(userId);
        WalletSummaryResponse wallet = safeGetWallet(userId);
        PagedResponse<WalletTransactionSummaryResponse> transactions =
                safeGetWalletTransactions(userId, transactionPage, transactionSize);
        PagedResponse<LoanSummaryResponse> loans =
                getLoans(null, userId, null, null, loanPage, loanSize);

        return CustomerDetailResponse.builder()
                .profile(profile)
                .wallet(wallet)
                .transactions(transactions)
                .loans(loans)
                .build();
    }

    private WalletSummaryResponse safeGetWallet(String userId) {
        try {
            URI uri = UriComponentsBuilder.fromHttpUrl(paymentServiceUrl)
                    .path("/internal/payment/wallet/{userId}")
                    .buildAndExpand(userId)
                    .toUri();
            return parseWallet(exchangeForJson(uri, HttpMethod.GET, null));
        } catch (Exception ex) {
            log.warn("Could not fetch wallet for customer {}: {}", userId, ex.getMessage());
            return null;
        }
    }

    private PagedResponse<WalletTransactionSummaryResponse> safeGetWalletTransactions(
            String userId, int page, int size) {
        try {
            URI uri = UriComponentsBuilder.fromHttpUrl(paymentServiceUrl)
                    .path("/internal/payment/wallet/{userId}/transactions")
                    .queryParam("page", page)
                    .queryParam("size", size)
                    .buildAndExpand(userId)
                    .toUri();
            return parseTransactionPage(exchangeForJson(uri, HttpMethod.GET, null), page, size);
        } catch (Exception ex) {
            log.warn("Could not fetch wallet transactions for customer {}: {}", userId, ex.getMessage());
            return PagedResponse.empty(page, size);
        }
    }

    public PagedResponse<SystemTransactionSummaryResponse> getSystemMoneyTransactions(
            String type,
            String status,
            LocalDate from,
            LocalDate to,
            String search,
            int page,
            int size) {
        URI uri = UriComponentsBuilder.fromHttpUrl(paymentServiceUrl)
                .path("/internal/payment/transactions")
                .queryParamIfPresent("type", Optional.ofNullable(type))
                .queryParamIfPresent("status", Optional.ofNullable(status))
                .queryParamIfPresent("from", Optional.ofNullable(from).map(LocalDate::toString))
                .queryParamIfPresent("to", Optional.ofNullable(to).map(LocalDate::toString))
                .queryParamIfPresent("search", Optional.ofNullable(search).filter(value -> !value.isBlank()))
                .queryParam("page", page)
                .queryParam("size", size)
                .build()
                .toUri();

        JsonNode pageNode = exchangeForJson(uri, HttpMethod.GET, null);
        Map<String, UserSummaryResponse> users = new HashMap<>();
        List<SystemTransactionSummaryResponse> content = new ArrayList<>();
        pageNode.path("content").forEach(node -> {
            String userId = text(node, "userId");
            UserSummaryResponse user = null;
            if (userId != null) {
                user = users.computeIfAbsent(userId, id -> {
                    try {
                        return getUser(id);
                    } catch (Exception ex) {
                        log.warn("Could not resolve customer profile for transaction userId={}: {}", id, ex.getMessage());
                        return null;
                    }
                });
            }
            content.add(SystemTransactionSummaryResponse.builder()
                    .id(text(node, "id"))
                    .userId(userId)
                    .customerName(user != null && user.getFullName() != null
                            ? user.getFullName() : user != null ? user.getPhone() : null)
                    .customerPhone(user != null ? user.getPhone() : null)
                    .walletId(text(node, "walletId"))
                    .vnfAccountNo(text(node, "vnfAccountNo"))
                    .type(text(node, "type"))
                    .amount(decimal(node, "amount"))
                    .status(text(node, "status"))
                    .description(text(node, "description"))
                    .referenceId(text(node, "referenceId"))
                    .externalRef(text(node, "externalRef"))
                    .balanceAfter(decimal(node, "balanceAfter"))
                    .createdAt(dateTime(node, "createdAt"))
                    .build());
        });

        return PagedResponse.<SystemTransactionSummaryResponse>builder()
                .content(content)
                .page(pageNode.has("page") ? pageNode.path("page").asInt() : pageNode.path("number").asInt(page))
                .size(pageNode.path("size").asInt(size))
                .totalElements(pageNode.path("totalElements").asLong())
                .totalPages(pageNode.path("totalPages").asInt())
                .last(pageNode.path("last").asBoolean(true))
                .build();
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

    public JsonNode getRepaymentMonitoring(int dueWithinDays) {
        String url = UriComponentsBuilder.fromHttpUrl(loanServiceUrl)
                .path("/internal/loans/repayment-monitoring")
                .queryParam("dueWithinDays", dueWithinDays)
                .toUriString();
        return exchangeForJson(url, HttpMethod.GET, null);
    }

    /**
     * Gợi ý hỗ trợ thẩm định — passthrough JSON nguyên bản từ loan-service.
     * creditGrade (hạng Credit 360) dùng để định giá lãi suất/hạn mức; null = chưa chấm điểm.
     */
    public JsonNode getAppraisalSuggestion(String loanId, boolean discouraged, String creditGrade) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(loanServiceUrl)
                .path("/internal/loans/{loanId}/appraisal-suggestion")
                .queryParam("discouraged", discouraged);
        if (creditGrade != null && !creditGrade.isBlank()) {
            builder.queryParam("creditGrade", creditGrade);
        }
        String url = builder.buildAndExpand(loanId).toUriString();
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

    /** Danh sách hợp đồng (vay + đầu tư) của một khoản — raw JSON cho CMS web. */
    public JsonNode getLoanContracts(String loanId) {
        String url = UriComponentsBuilder.fromHttpUrl(loanServiceUrl)
                .path("/internal/loans/{loanId}/contracts")
                .buildAndExpand(loanId)
                .toUriString();
        return exchangeForJson(url, HttpMethod.GET, null);
    }

    /** Danh sách chứng từ của một khoản — raw JSON cho CMS web. */
    public JsonNode getLoanDocuments(String loanId) {
        String url = UriComponentsBuilder.fromHttpUrl(loanServiceUrl)
                .path("/internal/loans/{loanId}/documents")
                .buildAndExpand(loanId)
                .toUriString();
        return exchangeForJson(url, HttpMethod.GET, null);
    }

    /** Số khoản gọi vốn đã hoàn thành của borrower — credit-service dùng để chấm điểm COMPLETED_LOANS. */
    public long getCompletedLoanCount(String borrowerId) {
        String url = UriComponentsBuilder.fromHttpUrl(loanServiceUrl)
                .path("/internal/loans/borrowers/{borrowerId}/completed-count")
                .buildAndExpand(borrowerId)
                .toUriString();
        JsonNode node = exchangeForJson(url, HttpMethod.GET, null);
        return node != null ? node.asLong(0) : 0;
    }

    /**
     * Chấm điểm tín dụng tham khảo cho khoản gọi vốn, gom dữ liệu từ loan-service + auth-service.
     * Gửi kèm toàn bộ chứng từ của khoản → credit-service AI phân tích hết rồi gộp vào advisory,
     * vì vậy dùng aiRestTemplate timeout dài.
     */
    public JsonNode evaluateCreditScore(String loanId) {
        return evaluateCreditScore(loanId, null);
    }

    /** Lấy điểm tín dụng gần nhất đã lưu theo khoản gọi vốn; null nếu chưa từng chấm. */
    public JsonNode getLatestCreditScore(String loanId) {
        String url = UriComponentsBuilder.fromHttpUrl(creditServiceUrl)
                .path("/internal/credit/scores/by-loan/{loanId}")
                .buildAndExpand(loanId)
                .toUriString();
        try {
            return exchangeForJson(url, HttpMethod.GET, null, aiRestTemplate);
        } catch (SourceServiceException ex) {
            if (ex.getStatusCode().value() == 404 || ex.getStatusCode().value() == 400) {
                return null;
            }
            throw ex;
        }
    }

    public JsonNode evaluateCreditScore(String loanId, com.p2plending.cms.domain.entity.CicManualLookup cic) {
        LoanSummaryResponse loan = getLoanById(loanId);
        UserSummaryResponse borrower = loan.getBorrowerId() != null ? safeGetUser(loan.getBorrowerId()) : null;

        long completedCount = 0;
        if (loan.getBorrowerId() != null) {
            try {
                completedCount = getCompletedLoanCount(loan.getBorrowerId());
            } catch (Exception ex) {
                log.warn("Could not fetch completed loan count for borrower {}: {}", loan.getBorrowerId(), ex.getMessage());
            }
        }

        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("userId", loan.getBorrowerId());
        body.put("loanRequestId", loan.getLoanId());
        body.put("loanAmount", loan.getAmount());
        body.put("interestRate", loan.getInterestRate());
        body.put("termMonths", loan.getTermMonths());
        body.put("purpose", loan.getPurpose());
        body.put("monthlyIncome", loan.getMonthlyIncome());
        body.put("occupation", loan.getOccupation());
        body.put("workplace", loan.getWorkplace());
        body.put("currentAddress", loan.getCurrentAddress());
        body.put("commune", loan.getCommune());
        body.put("province", loan.getProvince());
        body.put("ref1FullName", loan.getRef1FullName());
        body.put("ref1Relationship", loan.getRef1Relationship());
        body.put("ref1Phone", loan.getRef1Phone());
        body.put("ref1Address", loan.getRef1Address());
        body.put("ref2FullName", loan.getRef2FullName());
        body.put("ref2Relationship", loan.getRef2Relationship());
        body.put("ref2Phone", loan.getRef2Phone());
        body.put("ref2Address", loan.getRef2Address());
        body.put("hasReferrer", loan.getReferredBy() != null && !loan.getReferredBy().isBlank());
        body.put("completedLoanCount", (int) completedCount);
        if (borrower != null) {
            body.put("kycStatus", borrower.getKycStatus());
            body.put("accountCreatedAt", borrower.getCreatedAt());
            if (borrower.getDateOfBirth() != null) {
                body.put("dateOfBirth", borrower.getDateOfBirth().toString());
            }
        }
        body.put("declaredFullName", loan.getBorrowerName());
        body.put("declaredWorkplace", loan.getWorkplace());

        // CIC nhập tay (chờ API NĐ94) → chấm nhóm B. Null thì nhóm B báo thiếu để nhắc tra cứu.
        if (cic != null) {
            body.put("cicDebtGroup", cic.getDebtGroup());
            body.put("cicMaxDpd", cic.getMaxDpd());
            body.put("cicActiveLenders", cic.getActiveLenders());
            if (cic.getCheckedAt() != null) {
                body.put("cicCheckedAt", cic.getCheckedAt().toString());
            }
        }

        var documents = new ArrayList<java.util.Map<String, Object>>();
        try {
            for (JsonNode document : extractArray(getLoanDocuments(loanId))) {
                var doc = new java.util.LinkedHashMap<String, Object>();
                doc.put("docType", text(document, "docType"));
                doc.put("fileId", text(document, "fileId"));
                doc.put("fileName", text(document, "fileName"));
                if (text(document, "fileId") != null && !text(document, "fileId").isBlank()) {
                    documents.add(doc);
                }
            }
        } catch (Exception ex) {
            log.warn("Could not fetch documents of loan {} for credit scoring: {}", loanId, ex.getMessage());
        }
        log.info("Credit scoring loan {} includes {} supporting document(s)", loanId, documents.size());
        body.put("documents", documents);

        String url = UriComponentsBuilder.fromHttpUrl(creditServiceUrl)
                .path("/internal/credit/scores/evaluate")
                .toUriString();
        return exchangeForJson(url, HttpMethod.POST, body, aiRestTemplate);
    }

    /** AI phân tích một chứng từ của khoản gọi vốn, kết quả chỉ phục vụ thẩm định tham khảo. */
    public JsonNode analyzeLoanDocument(String loanId, String documentId) {
        LoanSummaryResponse loan = getLoanById(loanId);
        JsonNode document = findLoanDocument(loanId, documentId);

        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("userId", loan.getBorrowerId());
        body.put("loanRequestId", loan.getLoanId());
        body.put("docType", text(document, "docType"));
        body.put("fileId", text(document, "fileId"));
        body.put("fileName", text(document, "fileName"));
        body.put("declaredFullName", loan.getBorrowerName());
        body.put("declaredMonthlyIncome", loan.getMonthlyIncome());
        body.put("declaredOccupation", loan.getOccupation());
        body.put("declaredWorkplace", loan.getWorkplace());

        String url = UriComponentsBuilder.fromHttpUrl(creditServiceUrl)
                .path("/internal/credit/documents/analyze")
                .toUriString();
        return exchangeForJson(url, HttpMethod.POST, body, aiRestTemplate);
    }

    private Iterable<JsonNode> extractArray(JsonNode root) {
        if (root == null || root.isNull()) {
            return List.of();
        }
        if (root.isArray()) {
            return root;
        }
        for (String key : List.of("data", "content", "items", "documents")) {
            JsonNode child = root.path(key);
            if (child.isArray()) {
                return child;
            }
        }
        return List.of();
    }

    /** Danh sách sản phẩm gọi vốn đang hoạt động — passthrough JSON từ loan-service. */
    public JsonNode getLoanProducts() {
        String url = UriComponentsBuilder.fromHttpUrl(loanServiceUrl)
                .path("/api/loans/products")
                .toUriString();
        return exchangeForJson(url, HttpMethod.GET, null);
    }

    /** Cập nhật thông tin sản phẩm gọi vốn (CMS admin). */
    public JsonNode updateLoanProduct(String id, java.util.Map<String, Object> body) {
        String url = UriComponentsBuilder.fromHttpUrl(loanServiceUrl)
                .path("/internal/loans/products/{id}")
                .buildAndExpand(id)
                .toUriString();
        return exchangeForJson(url, HttpMethod.PUT, body);
    }

    // ─── Withdrawal Management (payment-service) ──────────────────────────────

    /**
     * Danh sách withdrawal để ops giám sát.
     * statuses: null/empty → dùng default (TRANSFER_FAILED,FAILED) bên payment-service.
     */
    public PagedResponse<WithdrawalSummaryResponse> getWithdrawalsForMonitoring(
            List<String> statuses, int page, int size) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(paymentServiceUrl)
                .path("/internal/payment/withdrawal/monitoring")
                .queryParam("page", page)
                .queryParam("size", size);
        if (statuses != null && !statuses.isEmpty()) {
            // Gửi multi-value: statuses=A&statuses=B để Spring Set<Enum> parse được
            statuses.stream().filter(s -> s != null && !s.isBlank())
                    .forEach(s -> builder.queryParam("statuses", s));
        }
        URI uri = builder.build().toUri();
        JsonNode node = exchangeForJson(uri, HttpMethod.GET, null);
        return parseWithdrawalPage(node, page, size);
    }

    /** Ops retry chuyển tiền thủ công khi giao dịch ở TRANSFER_FAILED. */
    public void retryWithdrawal(String adminId, String withdrawalId) {
        String url = UriComponentsBuilder.fromHttpUrl(paymentServiceUrl)
                .path("/internal/payment/withdrawal/{id}/retry")
                .buildAndExpand(withdrawalId)
                .toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.set(INTERNAL_SECRET_HEADER, internalSecret);
        headers.set("X-Admin-Id", adminId);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Void> entity = new HttpEntity<>(null, headers);
        exchangeAndParse(url, () -> restTemplate.exchange(url, HttpMethod.POST, entity, String.class));
    }

    /**
     * Ops resolve giao dịch kẹt ở PROCESSING sau khi đã xác minh tại TIKLUY/MB.
     * wasSent=true → đóng là đã chuyển (cần ftNumber); false → hoàn tiền về ví.
     */
    public void resolveWithdrawal(String adminId, String withdrawalId,
                                  boolean wasSent, String ftNumber, String note) {
        String url = UriComponentsBuilder.fromHttpUrl(paymentServiceUrl)
                .path("/internal/payment/withdrawal/{id}/resolve")
                .buildAndExpand(withdrawalId)
                .toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.set(INTERNAL_SECRET_HEADER, internalSecret);
        headers.set("X-Admin-Id", adminId);
        headers.setContentType(MediaType.APPLICATION_JSON);
        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("wasSent", wasSent);
        body.put("ftNumber", ftNumber);
        body.put("note", note);
        HttpEntity<Object> entity = new HttpEntity<>(body, headers);
        exchangeAndParse(url, () -> restTemplate.exchange(url, HttpMethod.POST, entity, String.class));
    }

    private PagedResponse<WithdrawalSummaryResponse> parseWithdrawalPage(JsonNode pageNode, int fallbackPage, int fallbackSize) {
        List<WithdrawalSummaryResponse> content = new ArrayList<>();
        pageNode.path("content").forEach(node -> content.add(parseWithdrawal(node)));
        return PagedResponse.<WithdrawalSummaryResponse>builder()
                .content(content)
                .page(pageNode.has("page") ? pageNode.path("page").asInt() : pageNode.path("number").asInt(fallbackPage))
                .size(pageNode.path("size").asInt(fallbackSize))
                .totalElements(pageNode.path("totalElements").asLong())
                .totalPages(pageNode.path("totalPages").asInt())
                .last(pageNode.path("last").asBoolean(true))
                .build();
    }

    private WithdrawalSummaryResponse parseWithdrawal(JsonNode node) {
        return WithdrawalSummaryResponse.builder()
                .id(text(node, "id"))
                .userId(text(node, "userId"))
                .customerPhone(text(node, "customerPhone"))
                .customerName(text(node, "customerName"))
                .amount(decimal(node, "amount"))
                .status(text(node, "status"))
                .statusLabel(text(node, "statusLabel"))
                .bankName(text(node, "bankName"))
                .bankAccountNo(text(node, "bankAccountNo"))
                .transferRef(text(node, "transferRef"))
                .mbFtNumber(text(node, "mbFtNumber"))
                .providerTransferRef(text(node, "providerTransferRef"))
                .rejectReason(text(node, "rejectReason"))
                .failureReason(text(node, "failureReason"))
                .retryCount(node.path("retryCount").asInt(0))
                .maxRetries(node.path("maxRetries").asInt(3))
                .createdAt(dateTime(node, "createdAt"))
                .updatedAt(dateTime(node, "updatedAt"))
                .build();
    }

    /** Chạy ngay job hết hạn gọi vốn / ký khế ước (CMS bấm tay). Trả về số khoản đã xử lý. */
    public JsonNode expireSweep() {
        String url = UriComponentsBuilder.fromHttpUrl(loanServiceUrl)
                .path("/internal/loans/expire-sweep")
                .toUriString();
        return exchangeForJson(url, HttpMethod.POST, null);
    }

    /** Giải ngân vốn cho người gọi vốn (OPS bấm trên CMS). */
    public LoanSummaryResponse disburseLoan(String loanId, String disbursedBy) {
        String url = UriComponentsBuilder.fromHttpUrl(loanServiceUrl)
                .path("/internal/loans/{loanId}/disburse")
                .buildAndExpand(loanId)
                .toUriString();
        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("disbursedBy", disbursedBy);
        return parseLoan(exchangeForJson(url, HttpMethod.POST, body));
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
                                           BigDecimal proposedInterestRate, BigDecimal appraisalFeeRate,
                                           String note, String proposedBy) {
        String url = UriComponentsBuilder.fromHttpUrl(loanServiceUrl)
                .path("/internal/loans/{loanId}/propose")
                .buildAndExpand(loanId)
                .toUriString();
        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("proposedAmount", proposedAmount);
        body.put("proposedInterestRate", proposedInterestRate);
        body.put("appraisalFeeRate", appraisalFeeRate);
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

    private JsonNode findLoanDocument(String loanId, String documentId) {
        JsonNode documents = getLoanDocuments(loanId);
        for (JsonNode document : documents) {
            if (documentId.equals(text(document, "id"))) {
                return document;
            }
        }
        throw new SourceServiceException(
                HttpStatus.NOT_FOUND,
                "Không tìm thấy chứng từ trong khoản gọi vốn này");
    }

    private UserSummaryResponse safeGetUser(String userId) {
        try {
            return getUser(userId);
        } catch (Exception ex) {
            log.warn("Could not fetch borrower {} for credit scoring: {}", userId, ex.getMessage());
            return null;
        }
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
        return exchangeForJson(url, method, body, restTemplate);
    }

    private JsonNode exchangeForJson(String url, HttpMethod method, Object body, RestTemplate template) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(INTERNAL_SECRET_HEADER, internalSecret);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Object> entity = new HttpEntity<>(body, headers);

        return exchangeAndParse(url, () -> template.exchange(url, method, entity, String.class));
    }

    private JsonNode exchangeForJson(URI uri, HttpMethod method, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(INTERNAL_SECRET_HEADER, internalSecret);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Object> entity = new HttpEntity<>(body, headers);

        return exchangeAndParse(uri.toString(), () -> restTemplate.exchange(uri, method, entity, String.class));
    }

    private JsonNode exchangeAndParse(String source, java.util.function.Supplier<ResponseEntity<String>> request) {
        ResponseEntity<String> response;
        try {
            response = request.get();
        } catch (RestClientResponseException ex) {
            String message = sourceErrorMessage(ex);
            log.warn("Source service HTTP error from {}: status={}, message={}", source, ex.getStatusCode(), message);
            throw new SourceServiceException(ex.getStatusCode(), message);
        } catch (Exception ex) {
            // Connection refused, read timeout, DNS failure, etc.
            log.error("Cannot connect to source service {}: {} — {}", source, ex.getClass().getSimpleName(), ex.getMessage());
            throw new SourceServiceException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "Không thể kết nối với máy chủ. Vui lòng thử lại.");
        }

        String body = response.getBody();
        if (body == null || body.isBlank()) {
            log.error("Source service {} returned empty body (status={})", source, response.getStatusCode());
            throw new SourceServiceException(
                    org.springframework.http.HttpStatus.BAD_GATEWAY,
                    "Service nguồn trả về nội dung trống");
        }

        try {
            return objectMapper.readTree(body);
        } catch (Exception ex) {
            log.error("Cannot parse JSON from source service {}: {} — body preview: {}",
                    source, ex.getMessage(), body.length() > 200 ? body.substring(0, 200) : body);
            throw new SourceServiceException(
                    org.springframework.http.HttpStatus.BAD_GATEWAY,
                    "Phản hồi từ service nguồn không hợp lệ (JSON parse error)");
        }
    }

    private String sourceErrorMessage(RestClientResponseException ex) {
        String body = ex.getResponseBodyAsString();
        if (body == null || body.isBlank()) {
            return "Service nguồn trả lỗi %s".formatted(ex.getStatusCode().value());
        }
        try {
            JsonNode node = objectMapper.readTree(body);
            if (node.hasNonNull("details") && node.get("details").isArray() && node.get("details").size() > 0) {
                return joinDetails(node.get("details"));
            }
            if (node.hasNonNull("message")) return node.get("message").asText();
            if (node.hasNonNull("detail")) return node.get("detail").asText();
            if (node.hasNonNull("error")) return node.get("error").asText();
        } catch (Exception ignored) {
            // Fall through to the raw body below.
        }
        return body.length() > 500 ? body.substring(0, 500) : body;
    }

    private String joinDetails(JsonNode details) {
        List<String> messages = new ArrayList<>();
        details.forEach(item -> messages.add(item.asText()));
        return String.join("; ", messages);
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
                .dateOfBirth(date(node, "dateOfBirth"))
                .gender(text(node, "gender"))
                .permanentAddress(text(node, "permanentAddress"))
                .hometown(text(node, "hometown"))
                .issueDate(date(node, "issueDate"))
                .issuingAuthority(text(node, "issuingAuthority"))
                .expiryDate(date(node, "expiryDate"))
                .frontImageId(text(node, "frontImageId"))
                .backImageId(text(node, "backImageId"))
                .portraitImageId(text(node, "portraitImageId"))
                .build();
    }

    private WalletSummaryResponse parseWallet(JsonNode node) {
        return WalletSummaryResponse.builder()
                .walletId(text(node, "walletId"))
                .vnfAccountNo(text(node, "vnfAccountNo"))
                .totalBalance(decimal(node, "totalBalance"))
                .lockedBalance(decimal(node, "lockedBalance"))
                .availableBalance(decimal(node, "availableBalance"))
                .createdAt(dateTime(node, "createdAt"))
                .build();
    }

    private PagedResponse<WalletTransactionSummaryResponse> parseTransactionPage(JsonNode pageNode, int fallbackPage, int fallbackSize) {
        List<WalletTransactionSummaryResponse> content = new ArrayList<>();
        pageNode.path("content").forEach(node -> content.add(parseTransaction(node)));
        return PagedResponse.<WalletTransactionSummaryResponse>builder()
                .content(content)
                .page(pageNode.has("page") ? pageNode.path("page").asInt() : pageNode.path("number").asInt(fallbackPage))
                .size(pageNode.path("size").asInt(fallbackSize))
                .totalElements(pageNode.path("totalElements").asLong())
                .totalPages(pageNode.path("totalPages").asInt())
                .last(pageNode.path("last").asBoolean(true))
                .build();
    }

    private WalletTransactionSummaryResponse parseTransaction(JsonNode node) {
        return WalletTransactionSummaryResponse.builder()
                .id(text(node, "id"))
                .type(text(node, "type"))
                .amount(decimal(node, "amount"))
                .status(text(node, "status"))
                .description(text(node, "description"))
                .balanceAfter(decimal(node, "balanceAfter"))
                .createdAt(dateTime(node, "createdAt"))
                .build();
    }

    private PagedResponse<LoanSummaryResponse> parseLoanPage(JsonNode pageNode) {
        List<LoanSummaryResponse> content = new ArrayList<>();
        pageNode.path("content").forEach(node -> {
            try {
                content.add(parseLoan(node));
            } catch (Exception ex) {
                log.warn("Skipping malformed loan entry — {}: {}", ex.getClass().getSimpleName(), ex.getMessage());
            }
        });
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
        UserSummaryResponse borrower = resolveBorrower(borrowerId);
        return LoanSummaryResponse.builder()
                .loanId(text(node, "id"))
                .loanCode(text(node, "loanCode"))
                .borrowerId(borrowerId)
                .borrowerName(resolveBorrowerName(borrower))
                .borrowerPhone(borrower != null ? borrower.getPhone() : null)
                .productName(text(node, "productName"))
                .amount(decimal(node, "amount"))
                .interestRate(decimal(node, "interestRate"))
                .proposedAmount(decimal(node, "proposedAmount"))
                .proposedInterestRate(decimal(node, "proposedInterestRate"))
                .appraisalFeeRate(decimal(node, "appraisalFeeRate"))
                .appraisalFee(decimal(node, "appraisalFee"))
                .vatAmount(decimal(node, "vatAmount"))
                .totalFee(decimal(node, "totalFee"))
                .netDisbursement(decimal(node, "netDisbursement"))
                .proposedBy(text(node, "proposedBy"))
                .proposedAt(dateTime(node, "proposedAt"))
                .appraisalNote(text(node, "appraisalNote"))
                .termMonths(node.hasNonNull("termMonths") ? node.get("termMonths").asInt() : null)
                .purpose(text(node, "purpose"))
                .ref1FullName(text(node, "ref1FullName"))
                .ref1Relationship(text(node, "ref1Relationship"))
                .ref1Phone(text(node, "ref1Phone"))
                .ref1Address(text(node, "ref1Address"))
                .ref2FullName(text(node, "ref2FullName"))
                .ref2Relationship(text(node, "ref2Relationship"))
                .ref2Phone(text(node, "ref2Phone"))
                .ref2Address(text(node, "ref2Address"))
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

    private UserSummaryResponse resolveBorrower(String borrowerId) {
        if (borrowerId == null) return null;
        try {
            return getUser(borrowerId);
        } catch (Exception ex) {
            log.warn("Could not resolve borrower profile for {}", borrowerId);
            return null;
        }
    }

    private String resolveBorrowerName(UserSummaryResponse borrower) {
        if (borrower == null) return null;
        return borrower.getFullName() != null ? borrower.getFullName() : borrower.getPhone();
    }

    // ─── Reconciliation ───────────────────────────────────────────────────────

    public JsonNode runReconciliation(LocalDate date, String runBy) {
        URI uri = UriComponentsBuilder.fromHttpUrl(paymentServiceUrl)
                .path("/internal/reconciliation/run")
                .queryParam("date", date.toString())
                .queryParam("runBy", runBy)
                .build()
                .toUri();
        return exchangeForJson(uri, HttpMethod.POST, null);
    }

    public JsonNode getReconciliationSessions(int page, int size) {
        URI uri = UriComponentsBuilder.fromHttpUrl(paymentServiceUrl)
                .path("/internal/reconciliation/sessions")
                .queryParam("page", page)
                .queryParam("size", size)
                .build()
                .toUri();
        return exchangeForJson(uri, HttpMethod.GET, null);
    }

    public JsonNode getReconciliationItems(String sessionId, String status, int page, int size) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(paymentServiceUrl)
                .path("/internal/reconciliation/sessions/{sessionId}/items")
                .queryParam("page", page)
                .queryParam("size", size);
        if (status != null && !status.isBlank()) {
            builder.queryParam("status", status);
        }
        URI uri = builder.buildAndExpand(sessionId).toUri();
        return exchangeForJson(uri, HttpMethod.GET, null);
    }

    public void resolveReconciliationItem(String itemId, String resolvedBy, String notes) {
        URI uri = UriComponentsBuilder.fromHttpUrl(paymentServiceUrl)
                .path("/internal/reconciliation/items/{itemId}/resolve")
                .buildAndExpand(itemId)
                .toUri();
        var body = new java.util.LinkedHashMap<String, String>();
        body.put("resolvedBy", resolvedBy);
        if (notes != null) body.put("notes", notes);
        exchangeForJson(uri, HttpMethod.PUT, body);
    }

    public void markReconciliationItemInvestigating(String itemId, String updatedBy) {
        URI uri = UriComponentsBuilder.fromHttpUrl(paymentServiceUrl)
                .path("/internal/reconciliation/items/{itemId}/investigate")
                .buildAndExpand(itemId)
                .toUri();
        var body = new java.util.LinkedHashMap<String, String>();
        body.put("updatedBy", updatedBy);
        exchangeForJson(uri, HttpMethod.PUT, body);
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
        if (!node.hasNonNull(field)) return null;
        String text = node.get(field).asText();
        if (text.isBlank()) return null;
        try {
            return LocalDateTime.parse(text, LENIENT_DT);
        } catch (Exception ex) {
            log.warn("Cannot parse dateTime field '{}' value '{}': {}", field, text, ex.getMessage());
            return null;
        }
    }

    private LocalDate date(JsonNode node, String field) {
        if (!node.hasNonNull(field)) return null;
        String text = node.get(field).asText();
        if (text.isBlank()) return null;
        try {
            return LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception ex) {
            log.warn("Cannot parse date field '{}' value '{}': {}", field, text, ex.getMessage());
            return null;
        }
    }

}
