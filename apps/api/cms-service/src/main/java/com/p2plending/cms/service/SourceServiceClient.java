package com.p2plending.cms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2plending.cms.domain.enums.UserAccountStatus;
import com.p2plending.cms.dto.request.LoanActionRequest;
import com.p2plending.cms.dto.response.CustomerDetailResponse;
import com.p2plending.cms.dto.response.InvestorCashflowResponse;
import com.p2plending.cms.dto.response.LoanOfferSummaryResponse;
import com.p2plending.cms.dto.response.LoanSummaryResponse;
import com.p2plending.cms.dto.response.PagedResponse;
import com.p2plending.cms.dto.response.ResetCustomerPasswordResponse;
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
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

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

    @Value("${external.vietqr.tax-lookup-url:https://api.vietqr.io/v2/business}")
    private String vietQrTaxLookupUrl;

    public PagedResponse<UserSummaryResponse> getUsers(
            String kycStatus, Boolean blacklisted, String role, UserAccountStatus status, String search, int page, int size) {
        URI uri = UriComponentsBuilder.fromHttpUrl(authServiceUrl)
                .path("/internal/users")
                .queryParamIfPresent("kycStatus", Optional.ofNullable(kycStatus))
                .queryParamIfPresent("blacklisted", Optional.ofNullable(blacklisted))
                .queryParamIfPresent("role", Optional.ofNullable(role))
                .queryParamIfPresent("search", Optional.ofNullable(search))
                .queryParam("page", page)
                .queryParam("size", size)
                .build()
                .encode()
                .toUri();

        JsonNode root = exchangeForJson(uri, HttpMethod.GET, null);
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
                                                    int loanPage, int loanSize,
                                                    int investmentPage, int investmentSize,
                                                    String investmentStatus) {
        UserSummaryResponse profile = getUser(userId);
        WalletSummaryResponse wallet = safeGetWallet(userId);
        PagedResponse<WalletTransactionSummaryResponse> transactions =
                safeGetWalletTransactions(userId, transactionPage, transactionSize);
        PagedResponse<LoanSummaryResponse> loans =
                getLoans(null, userId, null, null, null, loanPage, loanSize);
        enrichLoanOffers(loans);
        InvestorCashflowResponse investments = safeGetInvestorCashflow(userId,
                investmentPage, investmentSize, investmentStatus);

        return CustomerDetailResponse.builder()
                .profile(profile)
                .wallet(wallet)
                .transactions(transactions)
                .loans(loans)
                .investments(investments)
                .build();
    }

    // ─── Hồ sơ doanh nghiệp ──────────────────────────────────────────────────

    /** Danh sách hồ sơ doanh nghiệp chờ duyệt — passthrough JSON từ auth-service. */
    public JsonNode getBusinessProfiles(String status, int page, int size) {
        URI uri = UriComponentsBuilder.fromHttpUrl(authServiceUrl)
                .path("/internal/users/business-profiles")
                .queryParamIfPresent("status", Optional.ofNullable(status).filter(s -> !s.isBlank()))
                .queryParam("page", page)
                .queryParam("size", size)
                .build()
                .encode()
                .toUri();
        return exchangeForJson(uri, HttpMethod.GET, null);
    }

    /** Chi tiết hồ sơ doanh nghiệp của 1 user. */
    public JsonNode getBusinessProfile(String userId) {
        String url = UriComponentsBuilder.fromHttpUrl(authServiceUrl)
                .path("/internal/users/{userId}/business-profile")
                .buildAndExpand(userId)
                .toUriString();
        return exchangeForJson(url, HttpMethod.GET, null);
    }

    /** Đối chiếu dữ liệu MST cơ bản qua VietQR. Kết quả chỉ hỗ trợ thẩm định, không tự quyết định duyệt/từ chối. */
    public JsonNode lookupBusinessTax(String userId) {
        JsonNode profile = getBusinessProfile(userId);
        String expectedTaxCode = firstNonBlank(text(profile, "taxCode"), text(profile, "registrationNumber"));
        var result = objectMapper.createObjectNode();
        result.put("source", "VIETQR");
        result.put("lookupCode", expectedTaxCode);
        result.put("found", false);
        result.put("checkedAt", LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).toString());
        var warnings = objectMapper.createArrayNode();

        if (expectedTaxCode == null) {
            warnings.add("Hồ sơ chưa có mã số thuế hoặc số GCN đăng ký để tra cứu VietQR.");
            result.set("warnings", warnings);
            return result;
        }

        try {
            String url = UriComponentsBuilder.fromHttpUrl(vietQrTaxLookupUrl)
                    .pathSegment(expectedTaxCode)
                    .build()
                    .encode()
                    .toUriString();
            JsonNode root = externalGetForJson(url);
            String code = text(root, "code");
            String desc = text(root, "desc");
            JsonNode data = root.path("data");
            result.put("code", code);
            result.put("desc", desc);

            if (data.isObject() && data.hasNonNull("id")) {
                String taxId = text(data, "id");
                String name = text(data, "name");
                String internationalName = text(data, "internationalName");
                String shortName = text(data, "shortName");
                String address = text(data, "address");
                String status = text(data, "status");
                result.put("found", true);
                result.put("taxId", taxId);
                result.put("name", name);
                result.put("internationalName", internationalName);
                result.put("shortName", shortName);
                result.put("address", address);
                result.put("status", status);
                result.put("taxCodeMatched", sameDigits(taxId, expectedTaxCode));
                result.put("nameMatched", softMatch(name, text(profile, "businessName"))
                        || softMatch(shortName, text(profile, "businessName")));
                result.put("addressMatched", softMatch(address, text(profile, "headOfficeAddress")));
                JsonNode metadata = root.path("metadata");
                if (metadata.isObject()) {
                    result.put("dataSource", text(metadata, "source"));
                    result.put("dataUpdatedAt", text(metadata, "updatedAt"));
                    result.put("disclaimer", text(metadata, "disclaimer"));
                }
                warnings.add("VietQR chỉ trả dữ liệu cơ bản theo MST/tên/địa chỉ/trạng thái thuế, không có người đại diện, ngày cấp hoặc ngành nghề.");
            } else {
                warnings.add(desc != null && !desc.isBlank()
                        ? desc
                        : "VietQR không trả về dữ liệu cho mã tra cứu này.");
            }
        } catch (RestClientResponseException ex) {
            String message = sourceErrorMessage(ex);
            result.put("code", String.valueOf(ex.getStatusCode().value()));
            result.put("desc", message);
            warnings.add("Không tra cứu được VietQR: " + message);
        } catch (Exception ex) {
            log.warn("Cannot lookup VietQR business tax for user {}: {}", userId, ex.getMessage());
            warnings.add("Không thể kết nối VietQR. Vui lòng thử lại sau.");
        }
        result.set("warnings", warnings);
        return result;
    }

    /**
     * Duyệt/từ chối hồ sơ doanh nghiệp. Body: {approved, reason, reviewedBy, resolvedBusinessName}.
     * Khi duyệt, tự tra VietQR theo MST ngay tại thời điểm này — tra ra tên thì dùng làm tên chính
     * thức thay cho tên tự nhập; không tra ra thì auth-service giữ nguyên tên đã nộp.
     */
    public void decideBusinessProfile(String userId, boolean approved, String reason, String reviewedBy) {
        String url = UriComponentsBuilder.fromHttpUrl(authServiceUrl)
                .path("/internal/users/{userId}/business-profile/decision")
                .buildAndExpand(userId)
                .toUriString();
        Map<String, Object> body = new HashMap<>();
        body.put("approved", approved);
        body.put("reason", reason);
        body.put("reviewedBy", reviewedBy);
        if (approved) {
            try {
                JsonNode taxLookup = lookupBusinessTax(userId);
                if (taxLookup.path("found").asBoolean(false)) {
                    String resolvedName = firstNonBlank(text(taxLookup, "name"), text(taxLookup, "shortName"));
                    if (resolvedName != null) {
                        body.put("resolvedBusinessName", resolvedName);
                    }
                }
            } catch (Exception ex) {
                log.warn("Không tra được VietQR khi duyệt hồ sơ DN user {}, giữ tên tự nhập: {}", userId, ex.getMessage());
            }
        }
        exchangeForJson(url, HttpMethod.POST, body);
    }

    /**
     * AI đọc GPKD + đối chiếu thông tin khai báo (credit-service), rồi lưu verdict/summary
     * về hồ sơ trên auth-service để lần sau mở lại vẫn thấy. Kết quả chỉ tham khảo.
     */
    public JsonNode analyzeBusinessLicense(String userId) {
        JsonNode profile = getBusinessProfile(userId);

        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("userId", userId);
        body.put("fileId", text(profile, "licenseImageId"));
        body.put("fileIds", java.util.stream.Stream.of(
                        text(profile, "licenseImageId"),
                        text(profile, "licenseExtra1ImageId"),
                        text(profile, "licenseExtra2ImageId"))
                .filter(v -> v != null && !v.isBlank())
                .distinct()
                .toList());
        body.put("expectedBusinessName", text(profile, "businessName"));
        body.put("expectedRegistrationNumber", text(profile, "registrationNumber"));
        body.put("expectedTaxCode", text(profile, "taxCode"));
        body.put("expectedRepresentativeName", text(profile, "representativeName"));
        body.put("expectedRepresentativeCccd", text(profile, "representativeCccd"));
        body.put("expectedBusinessType", text(profile, "businessType"));

        String url = UriComponentsBuilder.fromHttpUrl(creditServiceUrl)
                .path("/internal/credit/business-license/analyze")
                .toUriString();
        JsonNode result = exchangeForJson(url, HttpMethod.POST, body, aiRestTemplate);

        // Lưu verdict + summary về auth-service (best-effort — lỗi lưu không chặn hiển thị kết quả)
        try {
            String saveUrl = UriComponentsBuilder.fromHttpUrl(authServiceUrl)
                    .path("/internal/users/{userId}/business-profile/ai-result")
                    .buildAndExpand(userId)
                    .toUriString();
            Map<String, Object> aiResult = new HashMap<>();
            aiResult.put("verdict", text(result, "verdict"));
            aiResult.put("summary", text(result, "summary"));
            exchangeForJson(saveUrl, HttpMethod.POST, aiResult);
        } catch (Exception e) {
            log.warn("Không lưu được kết quả AI GPKD cho user {}: {}", userId, e.getMessage());
        }
        return result;
    }

    // ─── Tin tức ────────────────────────────────────────────────────────────

    public JsonNode listNews(int page, int size, String type) {
        URI uri = UriComponentsBuilder.fromHttpUrl(loanServiceUrl)
                .path("/internal/news")
                .queryParam("page", page)
                .queryParam("size", size)
                .queryParamIfPresent("type", Optional.ofNullable(type).filter(value -> !value.isBlank()))
                .build()
                .encode()
                .toUri();
        return exchangeForJson(uri, HttpMethod.GET, null);
    }

    public JsonNode getNews(String id) {
        String url = UriComponentsBuilder.fromHttpUrl(loanServiceUrl)
                .path("/internal/news/{id}")
                .buildAndExpand(id)
                .toUriString();
        return exchangeForJson(url, HttpMethod.GET, null);
    }

    public JsonNode createNews(Map<String, Object> body) {
        String url = UriComponentsBuilder.fromHttpUrl(loanServiceUrl)
                .path("/internal/news")
                .toUriString();
        return exchangeForJson(url, HttpMethod.POST, body);
    }

    public JsonNode updateNews(String id, Map<String, Object> body) {
        String url = UriComponentsBuilder.fromHttpUrl(loanServiceUrl)
                .path("/internal/news/{id}")
                .buildAndExpand(id)
                .toUriString();
        return exchangeForJson(url, HttpMethod.PUT, body);
    }

    public void deleteNews(String id) {
        String url = UriComponentsBuilder.fromHttpUrl(loanServiceUrl)
                .path("/internal/news/{id}")
                .buildAndExpand(id)
                .toUriString();
        exchangeForVoid(url, HttpMethod.DELETE);
    }

    /** Xóa ảnh tin tức mồ côi (upload xong nhưng bài viết bị hủy trước khi lưu). Best-effort. */
    public void deleteNewsImage(String imageUrl) {
        URI uri = UriComponentsBuilder.fromHttpUrl(loanServiceUrl)
                .path("/internal/news/images")
                .queryParam("url", imageUrl)
                .build()
                .encode()
                .toUri();
        exchangeForVoid(uri, HttpMethod.DELETE);
    }

    public JsonNode uploadNewsImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new SourceServiceException(HttpStatus.BAD_REQUEST, "Vui lòng chọn ảnh");
        }
        String url = UriComponentsBuilder.fromHttpUrl(loanServiceUrl)
                .path("/internal/news/images")
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set(INTERNAL_SECRET_HEADER, internalSecret);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        try {
            body.add("file", new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            });
        } catch (IOException ex) {
            throw new SourceServiceException(HttpStatus.BAD_REQUEST, "Không thể đọc file ảnh");
        }

        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
        return exchangeAndParse(url, () -> restTemplate.exchange(url, HttpMethod.POST, entity, String.class));
    }

    // ─── Tuyển dụng ─────────────────────────────────────────────────────────

    public JsonNode listJobPostings(int page, int size, String status) {
        URI uri = UriComponentsBuilder.fromHttpUrl(loanServiceUrl)
                .path("/internal/job-postings")
                .queryParam("page", page)
                .queryParam("size", size)
                .queryParamIfPresent("status", Optional.ofNullable(status).filter(value -> !value.isBlank()))
                .build()
                .encode()
                .toUri();
        return exchangeForJson(uri, HttpMethod.GET, null);
    }

    public JsonNode getJobPosting(String id) {
        String url = UriComponentsBuilder.fromHttpUrl(loanServiceUrl)
                .path("/internal/job-postings/{id}")
                .buildAndExpand(id)
                .toUriString();
        return exchangeForJson(url, HttpMethod.GET, null);
    }

    public JsonNode createJobPosting(Map<String, Object> body) {
        String url = UriComponentsBuilder.fromHttpUrl(loanServiceUrl)
                .path("/internal/job-postings")
                .toUriString();
        return exchangeForJson(url, HttpMethod.POST, body);
    }

    public JsonNode updateJobPosting(String id, Map<String, Object> body) {
        String url = UriComponentsBuilder.fromHttpUrl(loanServiceUrl)
                .path("/internal/job-postings/{id}")
                .buildAndExpand(id)
                .toUriString();
        return exchangeForJson(url, HttpMethod.PUT, body);
    }

    public void deleteJobPosting(String id) {
        String url = UriComponentsBuilder.fromHttpUrl(loanServiceUrl)
                .path("/internal/job-postings/{id}")
                .buildAndExpand(id)
                .toUriString();
        exchangeForVoid(url, HttpMethod.DELETE);
    }

    /** Xóa ảnh tin tuyển dụng mồ côi (upload xong nhưng bài viết bị hủy trước khi lưu). Best-effort. */
    public void deleteJobImage(String imageUrl) {
        URI uri = UriComponentsBuilder.fromHttpUrl(loanServiceUrl)
                .path("/internal/job-postings/images")
                .queryParam("url", imageUrl)
                .build()
                .encode()
                .toUri();
        exchangeForVoid(uri, HttpMethod.DELETE);
    }

    public JsonNode uploadJobImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new SourceServiceException(HttpStatus.BAD_REQUEST, "Vui lòng chọn ảnh");
        }
        String url = UriComponentsBuilder.fromHttpUrl(loanServiceUrl)
                .path("/internal/job-postings/images")
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set(INTERNAL_SECRET_HEADER, internalSecret);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        try {
            body.add("file", new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            });
        } catch (IOException ex) {
            throw new SourceServiceException(HttpStatus.BAD_REQUEST, "Không thể đọc file ảnh");
        }

        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
        return exchangeAndParse(url, () -> restTemplate.exchange(url, HttpMethod.POST, entity, String.class));
    }

    public JsonNode listJobApplications(String jobPostingId, String keyword, String fromDate, String toDate, int page, int size) {
        URI uri = UriComponentsBuilder.fromHttpUrl(loanServiceUrl)
                .path("/internal/job-applications")
                .queryParamIfPresent("jobPostingId", Optional.ofNullable(jobPostingId).filter(value -> !value.isBlank()))
                .queryParamIfPresent("keyword", Optional.ofNullable(keyword).filter(value -> !value.isBlank()))
                .queryParamIfPresent("fromDate", Optional.ofNullable(fromDate).filter(value -> !value.isBlank()))
                .queryParamIfPresent("toDate", Optional.ofNullable(toDate).filter(value -> !value.isBlank()))
                .queryParam("page", page)
                .queryParam("size", size)
                .build()
                .encode()
                .toUri();
        return exchangeForJson(uri, HttpMethod.GET, null);
    }

    public void deleteJobApplication(String id) {
        String url = UriComponentsBuilder.fromHttpUrl(loanServiceUrl)
                .path("/internal/job-applications/{id}")
                .buildAndExpand(id)
                .toUriString();
        exchangeForVoid(url, HttpMethod.DELETE);
    }

    /** Tải CV ứng viên — trả nguyên response (bytes + header) từ loan-service để controller stream lại cho CMS. */
    public ResponseEntity<byte[]> downloadJobApplicationCv(String id) {
        String url = UriComponentsBuilder.fromHttpUrl(loanServiceUrl)
                .path("/internal/job-applications/{id}/cv")
                .buildAndExpand(id)
                .toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.set(INTERNAL_SECRET_HEADER, internalSecret);
        HttpEntity<Void> entity = new HttpEntity<>(null, headers);
        try {
            return restTemplate.exchange(url, HttpMethod.GET, entity, byte[].class);
        } catch (RestClientResponseException ex) {
            throw new SourceServiceException(ex.getStatusCode(), sourceErrorMessage(ex));
        } catch (Exception ex) {
            log.error("Cannot connect to loan-service {}: {} — {}", url, ex.getClass().getSimpleName(), ex.getMessage());
            throw new SourceServiceException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Không thể kết nối với máy chủ. Vui lòng thử lại.");
        }
    }

    public ResetCustomerPasswordResponse resetCustomerPassword(String userId) {
        String url = UriComponentsBuilder.fromHttpUrl(authServiceUrl)
                .path("/internal/users/{userId}/reset-password")
                .buildAndExpand(userId)
                .toUriString();
        JsonNode node = exchangeForJson(url, HttpMethod.POST, null);
        return ResetCustomerPasswordResponse.builder()
                .userId(text(node, "userId"))
                .phone(text(node, "phone"))
                .generatedPassword(text(node, "generatedPassword"))
                .build();
    }

    public void resetCustomerDevice(String userId) {
        String url = UriComponentsBuilder.fromHttpUrl(authServiceUrl)
                .path("/internal/users/{userId}/reset-device")
                .buildAndExpand(userId)
                .toUriString();
        exchangeForJson(url, HttpMethod.POST, null);
    }

    public UserSummaryResponse setCustomerBlacklist(String userId, boolean blacklisted, String reason) {
        String url = UriComponentsBuilder.fromHttpUrl(authServiceUrl)
                .path("/internal/users/{userId}/blacklist")
                .buildAndExpand(userId)
                .toUriString();
        Map<String, Object> body = new HashMap<>();
        body.put("blacklisted", blacklisted);
        if (reason != null && !reason.isBlank()) {
            body.put("reason", reason.trim());
        }
        return parseUser(exchangeForJson(url, HttpMethod.POST, body));
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

    private InvestorCashflowResponse safeGetInvestorCashflow(
            String userId, int page, int size, String status) {
        try {
            URI uri = UriComponentsBuilder.fromHttpUrl(loanServiceUrl)
                    .path("/internal/loans/investors/{investorId}/cashflow")
                    .buildAndExpand(userId)
                    .toUri();
            InvestorCashflowResponse response =
                    objectMapper.treeToValue(exchangeForJson(uri, HttpMethod.GET, null), InvestorCashflowResponse.class);
            enrichInvestmentBorrowers(response);
            paginateInvestments(response, page, size, status);
            return response;
        } catch (Exception ex) {
            log.warn("Could not fetch investment cashflow for customer {}: {}", userId, ex.getMessage());
            return InvestorCashflowResponse.builder()
                    .summary(InvestorCashflowResponse.Summary.builder()
                            .totalInvested(BigDecimal.ZERO)
                            .totalReturnsExpected(BigDecimal.ZERO)
                            .totalReturnsPaid(BigDecimal.ZERO)
                            .build())
                    .investmentHistory(List.of())
                    .investmentHistoryPage(PagedResponse.empty(page, size))
                    .upcomingPayments(List.of())
                    .monthlyChart(List.of())
                    .build();
        }
    }

    private void enrichInvestmentBorrowers(InvestorCashflowResponse response) {
        if (response == null || response.getInvestmentHistory() == null || response.getInvestmentHistory().isEmpty()) {
            return;
        }
        Map<String, UserSummaryResponse> borrowers = new HashMap<>();
        for (InvestorCashflowResponse.InvestmentItem item : response.getInvestmentHistory()) {
            String borrowerId = item.getBorrowerId();
            if (borrowerId == null || borrowerId.isBlank()) {
                continue;
            }
            UserSummaryResponse borrower = borrowers.computeIfAbsent(borrowerId, id -> {
                try {
                    return getUser(id);
                } catch (Exception ex) {
                    log.warn("Could not resolve investment borrower profile for {}: {}", id, ex.getMessage());
                    return null;
                }
            });
            if (borrower != null) {
                item.setBorrowerName(resolveBorrowerName(borrower));
                item.setBorrowerPhone(borrower.getPhone());
            }
        }
    }

    private void paginateInvestments(InvestorCashflowResponse response, int page, int size, String status) {
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = Math.min(Math.max(size, 1), 50);
        String normalizedStatus = status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
        Set<String> activePortfolioStatuses = Set.of(
                "ACTIVE",
                "FUNDED",
                "AWAITING_DISBURSEMENT",
                "DISBURSED",
                "REPAYING",
                "DEFAULTED"
        );
        List<InvestorCashflowResponse.InvestmentItem> filtered =
                Optional.ofNullable(response.getInvestmentHistory()).orElse(List.of()).stream()
                        .filter(item -> normalizedStatus == null
                                || ("ACTIVE_PORTFOLIO".equals(normalizedStatus)
                                    ? activePortfolioStatuses.contains(String.valueOf(item.getLoanStatus()).toUpperCase(Locale.ROOT))
                                    : normalizedStatus.equalsIgnoreCase(item.getLoanStatus())))
                        .toList();
        int from = Math.min(normalizedPage * normalizedSize, filtered.size());
        int to = Math.min(from + normalizedSize, filtered.size());
        List<InvestorCashflowResponse.InvestmentItem> content = filtered.subList(from, to);
        int totalPages = filtered.isEmpty() ? 0 : (int) Math.ceil((double) filtered.size() / normalizedSize);
        response.setInvestmentHistory(content);
        response.setInvestmentHistoryPage(PagedResponse.<InvestorCashflowResponse.InvestmentItem>builder()
                .content(content)
                .page(normalizedPage)
                .size(normalizedSize)
                .totalElements(filtered.size())
                .totalPages(totalPages)
                .last(totalPages == 0 || normalizedPage >= totalPages - 1)
                .build());
    }

    private void enrichLoanOffers(PagedResponse<LoanSummaryResponse> loans) {
        if (loans == null || loans.getContent() == null || loans.getContent().isEmpty()) {
            return;
        }
        for (LoanSummaryResponse loan : loans.getContent()) {
            if (loan.getLoanId() == null) {
                continue;
            }
            try {
                LoanSummaryResponse detailed = getLoanById(loan.getLoanId());
                loan.setOffers(detailed.getOffers());
            } catch (Exception ex) {
                log.warn("Could not fetch offers for borrower loan {}: {}", loan.getLoanId(), ex.getMessage());
                loan.setOffers(List.of());
            }
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
                .encode()
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
        URI uri = UriComponentsBuilder.fromHttpUrl(authServiceUrl)
                .path("/internal/users/stats")
                .queryParam("from", from.toString())
                .build()
                .encode()
                .toUri();
        return exchangeForJson(uri, HttpMethod.GET, null);
    }

    public com.fasterxml.jackson.databind.JsonNode getLoanStats(java.time.LocalDate from) {
        URI uri = UriComponentsBuilder.fromHttpUrl(loanServiceUrl)
                .path("/internal/loans/stats")
                .queryParam("from", from.toString())
                .build()
                .encode()
                .toUri();
        return exchangeForJson(uri, HttpMethod.GET, null);
    }

    public JsonNode getRepaymentMonitoring(int dueWithinDays) {
        URI uri = UriComponentsBuilder.fromHttpUrl(loanServiceUrl)
                .path("/internal/loans/repayment-monitoring")
                .queryParam("dueWithinDays", dueWithinDays)
                .build()
                .encode()
                .toUri();
        return exchangeForJson(uri, HttpMethod.GET, null);
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
        URI uri = builder.buildAndExpand(loanId).encode().toUri();
        return exchangeForJson(uri, HttpMethod.GET, null);
    }

    /** Lịch trả nợ của một khoản — passthrough JSON nguyên bản từ loan-service. */
    public JsonNode getRepaymentSchedule(String loanId) {
        String url = UriComponentsBuilder.fromHttpUrl(loanServiceUrl)
                .path("/internal/loans/{loanId}/repayments")
                .buildAndExpand(loanId)
                .toUriString();
        return exchangeForJson(url, HttpMethod.GET, null);
    }

    /**
     * Ghi nhận một lần trả nợ thủ công (admin nhập tay khi khách trả tiền mặt/chuyển khoản ngoài ví).
     * Áp tiền vào kỳ sớm nhất chưa trả → gốc+lãi trước, dư trả phí phạt. Passthrough JSON lịch trả nợ mới.
     */
    public JsonNode recordRepayment(String loanId, BigDecimal amount, String reason,
                                    String channel, String recordedBy, String note) {
        String url = UriComponentsBuilder.fromHttpUrl(loanServiceUrl)
                .path("/internal/loans/{loanId}/repayments")
                .buildAndExpand(loanId)
                .toUriString();
        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("amount", amount);
        body.put("reason", reason);
        if (channel != null && !channel.isBlank()) body.put("channel", channel);
        body.put("recordedBy", recordedBy);
        if (note != null && !note.isBlank()) body.put("note", note);
        return exchangeForJson(url, HttpMethod.POST, body);
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

    public JsonNode confirmPaperSignature(String contractId, String confirmedBy) {
        String url = UriComponentsBuilder.fromHttpUrl(loanServiceUrl)
                .path("/internal/loans/contracts/{contractId}/confirm-paper-signature")
                .buildAndExpand(contractId).toUriString();
        return exchangeForJson(url, HttpMethod.POST, Map.of("confirmedBy", confirmedBy));
    }

    public JsonNode confirmAllPaperSignatures(String loanId, String confirmedBy) {
        String url = UriComponentsBuilder.fromHttpUrl(loanServiceUrl)
                .path("/internal/loans/{loanId}/confirm-all-paper-signatures")
                .buildAndExpand(loanId).toUriString();
        return exchangeForJson(url, HttpMethod.POST, Map.of("confirmedBy", confirmedBy));
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
        URI uri = builder.build().encode().toUri();
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

    /**
     * Chạy ngay job thu nợ tự động từ ví người gọi vốn (CMS bấm tay).
     *
     * <p>Dùng {@code .toUri()} + overload nhận {@link URI} (không phải {@code String}) — nếu build
     * ra String đã encode rồi truyền cho {@code RestTemplate.exchange(String, ...)}, RestTemplate sẽ
     * encode lại lần nữa (dấu '%' bị coi là ký tự thường), khiến giá trị tiếng Việt như "Quản trị..."
     * bị lưu lại dạng double-encode nửa vời ở phía nhận.
     */
    public JsonNode autoDebitSweep(String triggeredBy) {
        URI uri = UriComponentsBuilder.fromHttpUrl(loanServiceUrl)
                .path("/internal/loans/repayments/auto-debit-sweep")
                .queryParam("triggeredBy", triggeredBy)
                .build()
                .encode()
                .toUri();
        return exchangeForJson(uri, HttpMethod.POST, null);
    }

    /** Sổ tất toán trước hạn — passthrough JSON từ loan-service. */
    public JsonNode getEarlySettlements(int page, int size) {
        String url = UriComponentsBuilder.fromHttpUrl(loanServiceUrl)
                .path("/internal/loans/early-settlements")
                .queryParam("page", page)
                .queryParam("size", size)
                .build()
                .encode()
                .toUriString();
        return exchangeForJson(url, HttpMethod.GET, null);
    }

    /** Báo giá tất toán trước hạn của 1 khoản (chỉ xem, không trừ tiền) — passthrough JSON từ loan-service. */
    public JsonNode getEarlySettlementQuote(String loanId) {
        String url = UriComponentsBuilder.fromHttpUrl(loanServiceUrl)
                .path("/internal/loans/{loanId}/early-settlement/quote")
                .buildAndExpand(loanId)
                .toUriString();
        return exchangeForJson(url, HttpMethod.GET, null);
    }

    /** Sổ cái doanh thu phí — tổng + danh sách phân trang, passthrough JSON từ loan-service. */
    public JsonNode getFeeRevenueReport(int page, int size) {
        String url = UriComponentsBuilder.fromHttpUrl(loanServiceUrl)
                .path("/internal/loans/stats/fee-revenue")
                .queryParam("page", page)
                .queryParam("size", size)
                .build()
                .encode()
                .toUriString();
        return exchangeForJson(url, HttpMethod.GET, null);
    }

    /** Lịch sử quét auto-debit — dùng cho CMS trang giám sát. */
    public JsonNode getAutoDebitAudit(int limit) {
        String url = UriComponentsBuilder.fromHttpUrl(loanServiceUrl)
                .path("/internal/loans/repayments/auto-debit-audit")
                .queryParam("limit", limit)
                .build()
                .encode()
                .toUriString();
        return exchangeForJson(url, HttpMethod.GET, null);
    }

    /** Chi tiết từng khoản trong một lần quét auto-debit, đã enrich thông tin người gọi vốn. */
    public JsonNode getAutoDebitAuditItems(String auditId) {
        String url = UriComponentsBuilder.fromHttpUrl(loanServiceUrl)
                .path("/internal/loans/repayments/auto-debit-audit/{auditId}/items")
                .buildAndExpand(auditId)
                .toUriString();
        JsonNode items = exchangeForJson(url, HttpMethod.GET, null);
        return enrichBorrowers(items);
    }

    /** Danh sách kỳ trả nợ đến hạn theo ngày, đã enrich thông tin người gọi vốn. */
    public JsonNode getDueTodaySchedules(String date) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(loanServiceUrl)
                .path("/internal/loans/repayments/due-today");
        if (date != null && !date.isBlank()) builder.queryParam("date", date);
        String url = builder.build().toUriString();
        JsonNode items = exchangeForJson(url, HttpMethod.GET, null);
        return enrichBorrowers(items);
    }

    private JsonNode enrichBorrowers(JsonNode items) {
        if (items == null || !items.isArray()) return items;

        Set<String> borrowerIds = new java.util.LinkedHashSet<>();
        items.forEach(item -> {
            JsonNode bid = item.get("borrowerId");
            if (bid != null && !bid.isNull() && !bid.asText("").isBlank()) borrowerIds.add(bid.asText());
        });

        Map<String, JsonNode> borrowerMap = new HashMap<>();
        for (String bid : borrowerIds) {
            try {
                String authUrl = UriComponentsBuilder.fromHttpUrl(authServiceUrl)
                        .path("/internal/users/{userId}")
                        .buildAndExpand(bid).toUriString();
                borrowerMap.put(bid, exchangeForJson(authUrl, HttpMethod.GET, null));
            } catch (Exception e) {
                log.warn("Không lấy được thông tin borrower {}: {}", bid, e.getMessage());
            }
        }

        com.fasterxml.jackson.databind.node.ArrayNode enriched = objectMapper.createArrayNode();
        items.forEach(item -> {
            com.fasterxml.jackson.databind.node.ObjectNode merged =
                    (com.fasterxml.jackson.databind.node.ObjectNode) item.deepCopy();
            String bid = item.path("borrowerId").asText(null);
            JsonNode borrow = bid == null ? null : borrowerMap.get(bid);
            merged.put("borrowerPhone", borrow == null ? "" : borrow.path("phone").asText(""));
            merged.put("borrowerFullName", borrow == null ? "" : borrow.path("fullName").asText(""));
            enriched.add(merged);
        });
        return enriched;
    }

    /** Log phân bổ nhà đầu tư (thuế TNCN) — dùng cho CMS kế toán. */
    public JsonNode getDistributionLog(String loanId, String investorId, int page, int size) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(loanServiceUrl)
                .path("/internal/loans/repayments/distribution-log")
                .queryParam("page", page)
                .queryParam("size", size);
        if (loanId != null && !loanId.isBlank())     builder.queryParam("loanId", loanId);
        if (investorId != null && !investorId.isBlank()) builder.queryParam("investorId", investorId);
        return enrichDistributionInvestors(exchangeForJson(builder.build().encode().toUri(), HttpMethod.GET, null));
    }

    private JsonNode enrichDistributionInvestors(JsonNode page) {
        JsonNode content = page == null ? null : page.get("content");
        if (content == null || !content.isArray()) return page;

        Set<String> investorIds = new java.util.LinkedHashSet<>();
        content.forEach(item -> {
            JsonNode id = item.get("investorId");
            if (id != null && !id.isNull() && !id.asText("").isBlank()) investorIds.add(id.asText());
        });

        Map<String, JsonNode> investorMap = new HashMap<>();
        for (String id : investorIds) {
            try {
                String authUrl = UriComponentsBuilder.fromHttpUrl(authServiceUrl)
                        .path("/internal/users/{userId}")
                        .buildAndExpand(id).toUriString();
                investorMap.put(id, exchangeForJson(authUrl, HttpMethod.GET, null));
            } catch (Exception e) {
                log.warn("Không lấy được thông tin nhà đầu tư {}: {}", id, e.getMessage());
            }
        }

        com.fasterxml.jackson.databind.node.ArrayNode enriched = objectMapper.createArrayNode();
        content.forEach(item -> {
            com.fasterxml.jackson.databind.node.ObjectNode merged =
                    (com.fasterxml.jackson.databind.node.ObjectNode) item.deepCopy();
            String id = item.path("investorId").asText(null);
            JsonNode investor = id == null ? null : investorMap.get(id);
            merged.put("investorName", investor == null ? "" : investor.path("fullName").asText(""));
            merged.put("investorPhone", investor == null ? "" : investor.path("phone").asText(""));
            enriched.add(merged);
        });

        com.fasterxml.jackson.databind.node.ObjectNode result =
                (com.fasterxml.jackson.databind.node.ObjectNode) page.deepCopy();
        result.set("content", enriched);
        return result;
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
                                                       String province, String search,
                                                       String productCategories, int page, int size) {
        boolean overdueOnly    = "OVERDUE".equalsIgnoreCase(String.valueOf(status));
        boolean repayingGroup  = "REPAYING".equalsIgnoreCase(String.valueOf(status));
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(loanServiceUrl)
                .path("/internal/loans")
                .queryParamIfPresent("borrowerId", Optional.ofNullable(borrowerId))
                .queryParamIfPresent("province",   Optional.ofNullable(province))
                .queryParamIfPresent("search",     Optional.ofNullable(search))
                .queryParamIfPresent("productCategories", Optional.ofNullable(productCategories))
                .queryParam("page", page)
                .queryParam("size", size)
                .queryParam("sortBy", "createdAt")
                .queryParam("sortDir", "desc");
        if (overdueOnly) {
            builder.queryParam("overdueOnly", true)
                    .queryParam("statuses", "DISBURSED", "REPAYING", "DEFAULTED");
        } else if (repayingGroup) {
            // Khoản DEFAULTED vẫn chưa trả xong — hiển thị cùng "Đang thanh toán"
            builder.queryParam("statuses", "REPAYING", "DEFAULTED");
        } else {
            builder.queryParamIfPresent("status", Optional.ofNullable(status));
        }
        URI uri = builder
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

    public LoanSummaryResponse cancelLoan(String loanId, LoanActionRequest request, String cancelledBy) {
        return reviewLoan(loanId, "cancel", request, cancelledBy);
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
        body.put("approvedAmount", request.getApprovedAmount());
        body.put("interestRate", request.getInterestRate());
        body.put("termMonths", request.getTermMonths());
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

    private void exchangeForVoid(String url, HttpMethod method) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(INTERNAL_SECRET_HEADER, internalSecret);
        HttpEntity<Object> entity = new HttpEntity<>(null, headers);

        try {
            restTemplate.exchange(url, method, entity, Void.class);
        } catch (RestClientResponseException ex) {
            String message = sourceErrorMessage(ex);
            log.warn("Source service HTTP error from {}: status={}, message={}", url, ex.getStatusCode(), message);
            throw new SourceServiceException(ex.getStatusCode(), message);
        } catch (Exception ex) {
            log.error("Cannot connect to source service {}: {} — {}", url, ex.getClass().getSimpleName(), ex.getMessage());
            throw new SourceServiceException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "Không thể kết nối với máy chủ. Vui lòng thử lại.");
        }
    }

    private void exchangeForVoid(URI uri, HttpMethod method) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(INTERNAL_SECRET_HEADER, internalSecret);
        HttpEntity<Object> entity = new HttpEntity<>(null, headers);

        try {
            restTemplate.exchange(uri, method, entity, Void.class);
        } catch (RestClientResponseException ex) {
            String message = sourceErrorMessage(ex);
            log.warn("Source service HTTP error from {}: status={}, message={}", uri, ex.getStatusCode(), message);
            throw new SourceServiceException(ex.getStatusCode(), message);
        } catch (Exception ex) {
            log.error("Cannot connect to source service {}: {} — {}", uri, ex.getClass().getSimpleName(), ex.getMessage());
            throw new SourceServiceException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "Không thể kết nối với máy chủ. Vui lòng thử lại.");
        }
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
                .blacklisted(node.path("blacklisted").asBoolean(false))
                .blacklistedAt(dateTime(node, "blacklistedAt"))
                .blacklistSource(text(node, "blacklistSource"))
                .blacklistReason(text(node, "blacklistReason"))
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
        String productCategory = text(node, "productCategory");
        JsonNode businessProfile = isBusinessFundingCategory(productCategory)
                ? safeGetBusinessProfile(borrowerId)
                : null;
        return LoanSummaryResponse.builder()
                .loanId(text(node, "id"))
                .loanCode(text(node, "loanCode"))
                .borrowerId(borrowerId)
                .borrowerName(resolveBorrowerName(borrower))
                .borrowerPhone(borrower != null ? borrower.getPhone() : null)
                .borrowerEmail(borrower != null ? borrower.getEmail() : null)
                .borrowerCccdNumber(borrower != null ? borrower.getCccdNumber() : null)
                .borrowerKycStatus(borrower != null ? borrower.getKycStatus() : null)
                .borrowerAccountStatus(borrower != null && borrower.getAccountStatus() != null ? borrower.getAccountStatus().name() : null)
                .borrowerDateOfBirth(borrower != null ? borrower.getDateOfBirth() : null)
                .borrowerGender(borrower != null ? borrower.getGender() : null)
                .borrowerPermanentAddress(borrower != null ? borrower.getPermanentAddress() : null)
                .borrowerHometown(borrower != null ? borrower.getHometown() : null)
                .borrowerIssueDate(borrower != null ? borrower.getIssueDate() : null)
                .borrowerIssuingAuthority(borrower != null ? borrower.getIssuingAuthority() : null)
                .borrowerExpiryDate(borrower != null ? borrower.getExpiryDate() : null)
                .borrowerFrontImageId(borrower != null ? borrower.getFrontImageId() : null)
                .borrowerBackImageId(borrower != null ? borrower.getBackImageId() : null)
                .borrowerPortraitImageId(borrower != null ? borrower.getPortraitImageId() : null)
                .businessType(text(businessProfile, "businessType"))
                .businessName(text(businessProfile, "businessName"))
                .businessRepresentativeName(text(businessProfile, "representativeName"))
                .productName(text(node, "productName"))
                .productCategory(productCategory)
                .amount(decimal(node, "amount"))
                .fundedAmount(decimal(node, "fundedAmount"))
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
                .offers(parseOffers(node.path("offers")))
                .build();
    }

    private boolean isBusinessFundingCategory(String productCategory) {
        return "BUSINESS".equalsIgnoreCase(productCategory) || "ENTERPRISE".equalsIgnoreCase(productCategory);
    }

    private JsonNode safeGetBusinessProfile(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        try {
            return getBusinessProfile(userId);
        } catch (Exception ex) {
            log.warn("Could not resolve business profile for borrower {}: {}", userId, ex.getMessage());
            return null;
        }
    }

    private List<LoanOfferSummaryResponse> parseOffers(JsonNode offersNode) {
        if (offersNode == null || !offersNode.isArray()) {
            return List.of();
        }
        List<LoanOfferSummaryResponse> offers = new ArrayList<>();
        Map<String, UserSummaryResponse> investors = new HashMap<>();
        offersNode.forEach(node -> {
            String investorId = text(node, "investorId");
            UserSummaryResponse investor = null;
            if (investorId != null) {
                investor = investors.computeIfAbsent(investorId, id -> {
                    try {
                        return getUser(id);
                    } catch (Exception ex) {
                        log.warn("Could not resolve investment investor profile for {}: {}", id, ex.getMessage());
                        return null;
                    }
                });
            }
            offers.add(LoanOfferSummaryResponse.builder()
                    .offerId(text(node, "id"))
                    .investorId(investorId)
                    .investorName(resolveBorrowerName(investor))
                    .investorPhone(investor != null ? investor.getPhone() : null)
                    .amount(decimal(node, "amount"))
                    .status(text(node, "status"))
                    .createdAt(dateTime(node, "createdAt"))
                    .build());
        });
        return offers;
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

    public JsonNode runReconciliation(LocalDate date, String runBy, boolean autoFixDeposits) {
        URI uri = UriComponentsBuilder.fromHttpUrl(paymentServiceUrl)
                .path("/internal/reconciliation/run")
                .queryParam("date", date.toString())
                .queryParam("runBy", runBy)
                .queryParam("autoFixDeposits", autoFixDeposits)
                .build()
                .encode()
                .toUri();
        return exchangeForJson(uri, HttpMethod.POST, null);
    }

    public JsonNode getReconciliationSessions(int page, int size) {
        URI uri = UriComponentsBuilder.fromHttpUrl(paymentServiceUrl)
                .path("/internal/reconciliation/sessions")
                .queryParam("page", page)
                .queryParam("size", size)
                .build()
                .encode()
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
        URI uri = builder.buildAndExpand(sessionId).encode().toUri();
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

    public void backfillMissingDeposit(String itemId, String resolvedBy) {
        URI uri = UriComponentsBuilder.fromHttpUrl(paymentServiceUrl)
                .path("/internal/reconciliation/items/{itemId}/backfill-deposit")
                .buildAndExpand(itemId)
                .toUri();
        var body = new java.util.LinkedHashMap<String, String>();
        body.put("resolvedBy", resolvedBy);
        exchangeForJson(uri, HttpMethod.POST, body);
    }

    private JsonNode externalGetForJson(String url) {
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, HttpEntity.EMPTY, String.class);
        String body = response.getBody();
        if (body == null || body.isBlank()) {
            throw new SourceServiceException(HttpStatus.BAD_GATEWAY, "VietQR trả về nội dung trống");
        }
        try {
            return objectMapper.readTree(body);
        } catch (Exception ex) {
            log.warn("Cannot parse VietQR response from {}: {}", url, ex.getMessage());
            throw new SourceServiceException(HttpStatus.BAD_GATEWAY, "Phản hồi VietQR không hợp lệ");
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return null;
    }

    private boolean sameDigits(String left, String right) {
        String a = digitsOnly(left);
        String b = digitsOnly(right);
        return !a.isBlank() && a.equals(b);
    }

    private String digitsOnly(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    private boolean softMatch(String left, String right) {
        String a = normalizeForMatch(left);
        String b = normalizeForMatch(right);
        if (a.isBlank() || b.isBlank()) return false;
        return a.equals(b) || a.contains(b) || b.contains(a);
    }

    private String normalizeForMatch(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase(Locale.ROOT);
        return Pattern.compile("[^a-z0-9]+").matcher(normalized).replaceAll(" ").trim();
    }

    private UserAccountStatus parseAccountStatus(String value) {
        if (value == null) return null;
        return UserAccountStatus.valueOf(value);
    }

    private String text(JsonNode node, String field) {
        if (node == null || node.isNull()) return null;
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
