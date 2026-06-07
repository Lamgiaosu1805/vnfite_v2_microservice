package com.p2plending.auth.controller;

import com.p2plending.auth.domain.enums.KycStatus;
import com.p2plending.auth.dto.response.InternalUserStatsResponse;
import com.p2plending.auth.dto.response.InternalUserSummaryResponse;
import com.p2plending.auth.dto.response.PagedResponse;
import com.p2plending.auth.service.FcmTokenService;
import com.p2plending.auth.service.InternalUserQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class InternalUserController {

    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";

    private final InternalUserQueryService userQueryService;
    private final FcmTokenService          fcmTokenService;

    @Value("${app.internal.secret:dev-internal-secret}")
    private String internalSecret;

    @GetMapping
    public ResponseEntity<PagedResponse<InternalUserSummaryResponse>> getUsers(
            @RequestHeader(INTERNAL_SECRET_HEADER) String secret,
            @RequestParam(required = false) KycStatus kycStatus,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireInternalSecret(secret);
        return ResponseEntity.ok(userQueryService.getUsers(kycStatus, role, search, page, size));
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

    private void requireInternalSecret(String secret) {
        if (!internalSecret.equals(secret)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid internal secret");
        }
    }
}
