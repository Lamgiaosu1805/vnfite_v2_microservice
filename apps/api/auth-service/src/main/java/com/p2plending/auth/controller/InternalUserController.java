package com.p2plending.auth.controller;

import com.p2plending.auth.domain.enums.KycStatus;
import com.p2plending.auth.dto.request.BlacklistInternalRequest;
import com.p2plending.auth.dto.request.BusinessProfileDecisionInternalRequest;
import com.p2plending.auth.dto.request.KycDecisionInternalRequest;
import com.p2plending.auth.dto.response.BusinessProfileResponse;
import com.p2plending.auth.dto.response.InternalCustomerPasswordResetResponse;
import com.p2plending.auth.dto.response.InternalUserStatsResponse;
import com.p2plending.auth.dto.response.InternalUserSummaryResponse;
import com.p2plending.auth.dto.response.PagedResponse;
import com.p2plending.auth.service.BusinessProfileService;
import com.p2plending.auth.service.FcmTokenService;
import com.p2plending.auth.service.InternalCustomerAdminService;
import com.p2plending.auth.service.InternalUserQueryService;
import com.p2plending.auth.service.KycDecisionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class InternalUserController {

    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";

    private final InternalUserQueryService userQueryService;
    private final FcmTokenService          fcmTokenService;
    private final KycDecisionService       kycDecisionService;
    private final InternalCustomerAdminService customerAdminService;
    private final BusinessProfileService   businessProfileService;

    @Value("${app.internal.secret}")
    private String internalSecret;

    @GetMapping
    public ResponseEntity<PagedResponse<InternalUserSummaryResponse>> getUsers(
            @RequestHeader(INTERNAL_SECRET_HEADER) String secret,
            @RequestParam(required = false) KycStatus kycStatus,
            @RequestParam(required = false) Boolean blacklisted,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireInternalSecret(secret);
        return ResponseEntity.ok(userQueryService.getUsers(kycStatus, blacklisted, role, search, page, size));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<InternalUserSummaryResponse> getUser(
            @RequestHeader(INTERNAL_SECRET_HEADER) String secret,
            @PathVariable String userId) {
        requireInternalSecret(secret);
        return ResponseEntity.ok(userQueryService.getUser(userId));
    }

    /** Thống kê tổng hợp — dùng cho CMS dashboard */
    @GetMapping("/stats")
    public ResponseEntity<InternalUserStatsResponse> getStats(
            @RequestHeader(INTERNAL_SECRET_HEADER) String secret,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from) {
        requireInternalSecret(secret);
        return ResponseEntity.ok(userQueryService.getStats(from));
    }

    /**
     * GET /internal/users/{userId}/fcm-token
     * notification-service gọi để lấy FCM token trước khi push notification.
     * Response: { "fcmToken": "..." } hoặc 204 No Content nếu không có.
     */
    @GetMapping("/{userId}/fcm-token")
    public ResponseEntity<Map<String, String>> getFcmToken(
            @RequestHeader(INTERNAL_SECRET_HEADER) String secret,
            @PathVariable String userId) {
        requireInternalSecret(secret);
        return fcmTokenService.getToken(userId)
                .map(token -> ResponseEntity.ok(Map.of("fcmToken", token)))
                .orElse(ResponseEntity.noContent().build());
    }

    /**
     * GET /internal/users/fcm-tokens-all
     * Trả về danh sách tất cả FCM token đang active — dùng cho broadcast push notification.
     * Response: { "tokens": [...], "count": N }
     */
    @GetMapping("/fcm-tokens-all")
    public ResponseEntity<Map<String, Object>> getAllFcmTokens(
            @RequestHeader(INTERNAL_SECRET_HEADER) String secret) {
        requireInternalSecret(secret);
        List<String> tokens = fcmTokenService.getAllTokens();
        return ResponseEntity.ok(Map.of("tokens", tokens, "count", tokens.size()));
    }

    /**
     * PUT /internal/users/{userId}/kyc-decision
     * CMS admin duyệt hoặc từ chối KYC.
     * Khi duyệt: publish kyc.approved → payment-service tạo ví + VNF account.
     * Khi từ chối: user.kyc_status reset về NONE để có thể nộp lại.
     */
    @PostMapping("/{userId}/kyc-decision")
    public ResponseEntity<Void> kycDecision(
            @RequestHeader(INTERNAL_SECRET_HEADER) String secret,
            @PathVariable String userId,
            @Valid @RequestBody KycDecisionInternalRequest request) {
        requireInternalSecret(secret);
        kycDecisionService.decide(userId, request.isApproved(), request.getReason());
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /internal/users/business-profiles?status=&page=&size=
     * Danh sách hồ sơ doanh nghiệp cho CMS duyệt (mới nhất trước).
     */
    @GetMapping("/business-profiles")
    public ResponseEntity<PagedResponse<BusinessProfileResponse>> getBusinessProfiles(
            @RequestHeader(INTERNAL_SECRET_HEADER) String secret,
            @RequestParam(required = false) KycStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireInternalSecret(secret);
        return ResponseEntity.ok(businessProfileService.list(status, page, size));
    }

    /** GET /internal/users/{userId}/business-profile — chi tiết hồ sơ DN của 1 user. */
    @GetMapping("/{userId}/business-profile")
    public ResponseEntity<BusinessProfileResponse> getBusinessProfile(
            @RequestHeader(INTERNAL_SECRET_HEADER) String secret,
            @PathVariable String userId) {
        requireInternalSecret(secret);
        return ResponseEntity.ok(businessProfileService.getByUserId(userId));
    }

    /**
     * POST /internal/users/{userId}/business-profile/decision
     * CMS admin duyệt/từ chối hồ sơ doanh nghiệp. Duyệt → account_type BUSINESS|ENTERPRISE
     * + publish business-profile.approved (giai đoạn B: payment-service tạo ví DN).
     */
    @PostMapping("/{userId}/business-profile/decision")
    public ResponseEntity<Void> businessProfileDecision(
            @RequestHeader(INTERNAL_SECRET_HEADER) String secret,
            @PathVariable String userId,
            @Valid @RequestBody BusinessProfileDecisionInternalRequest request) {
        requireInternalSecret(secret);
        businessProfileService.decide(userId, request.isApproved(), request.getReason(), request.getReviewedBy(),
                request.getResolvedBusinessName());
        return ResponseEntity.noContent().build();
    }

    /** POST /internal/users/{userId}/business-profile/ai-result — lưu kết quả AI tham khảo. */
    @PostMapping("/{userId}/business-profile/ai-result")
    public ResponseEntity<Void> saveBusinessProfileAiResult(
            @RequestHeader(INTERNAL_SECRET_HEADER) String secret,
            @PathVariable String userId,
            @RequestBody Map<String, String> body) {
        requireInternalSecret(secret);
        businessProfileService.saveAiResult(userId, body.get("verdict"), body.get("summary"));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{userId}/reset-password")
    public ResponseEntity<InternalCustomerPasswordResetResponse> resetCustomerPassword(
            @RequestHeader(INTERNAL_SECRET_HEADER) String secret,
            @PathVariable String userId) {
        requireInternalSecret(secret);
        return ResponseEntity.ok(customerAdminService.resetPassword(userId));
    }

    @PostMapping("/{userId}/reset-device")
    public ResponseEntity<Map<String, String>> resetCustomerDevice(
            @RequestHeader(INTERNAL_SECRET_HEADER) String secret,
            @PathVariable String userId) {
        requireInternalSecret(secret);
        customerAdminService.resetDevice(userId);
        return ResponseEntity.ok(Map.of("message", "Đã đặt lại thiết bị khách hàng"));
    }

    @PostMapping("/{userId}/blacklist")
    public ResponseEntity<InternalUserSummaryResponse> setBlacklist(
            @RequestHeader(INTERNAL_SECRET_HEADER) String secret,
            @PathVariable String userId,
            @Valid @RequestBody BlacklistInternalRequest request) {
        requireInternalSecret(secret);
        customerAdminService.setBlacklist(userId, request.isBlacklisted(), request.getReason());
        return ResponseEntity.ok(userQueryService.getUser(userId));
    }

    private void requireInternalSecret(String secret) {
        if (!internalSecret.equals(secret)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid internal secret");
        }
    }
}
