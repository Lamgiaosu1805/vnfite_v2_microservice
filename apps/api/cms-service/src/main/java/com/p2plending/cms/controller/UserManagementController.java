package com.p2plending.cms.controller;

import com.p2plending.cms.domain.enums.UserAccountStatus;
import com.p2plending.cms.dto.request.KycDecisionRequest;
import com.p2plending.cms.dto.request.UserBlacklistRequest;
import com.p2plending.cms.dto.request.UserStatusRequest;
import com.p2plending.cms.dto.response.CustomerDetailResponse;
import com.p2plending.cms.dto.response.PagedResponse;
import com.p2plending.cms.dto.response.ResetCustomerPasswordResponse;
import com.p2plending.cms.dto.response.UserSummaryResponse;
import com.p2plending.cms.security.CmsPrincipal;
import com.p2plending.cms.service.UserManagementService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/cms/users")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'OPS', 'CUSTOMER_SUPPORT')")
public class UserManagementController {

    private final UserManagementService userService;

    @GetMapping
    public ResponseEntity<PagedResponse<UserSummaryResponse>> getUsers(
            @RequestParam(required = false) String kycStatus,
            @RequestParam(required = false) Boolean blacklisted,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) UserAccountStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(userService.getUsers(kycStatus, blacklisted, role, status, search, page, size));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserSummaryResponse> getUser(@PathVariable String userId) {
        return ResponseEntity.ok(userService.getUser(userId));
    }

    @GetMapping("/{userId}/detail")
    public ResponseEntity<CustomerDetailResponse> getCustomerDetail(
            @PathVariable String userId,
            @RequestParam(defaultValue = "0") int transactionPage,
            @RequestParam(defaultValue = "20") int transactionSize,
            @RequestParam(defaultValue = "0") int loanPage,
            @RequestParam(defaultValue = "20") int loanSize,
            @RequestParam(defaultValue = "0") int investmentPage,
            @RequestParam(defaultValue = "10") int investmentSize,
            @RequestParam(required = false) String investmentStatus) {
        return ResponseEntity.ok(userService.getCustomerDetail(
                userId, transactionPage, transactionSize, loanPage, loanSize,
                investmentPage, investmentSize, investmentStatus));
    }

    @PutMapping("/{userId}/kyc")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'CUSTOMER_SUPPORT') or hasAuthority('kyc.decide')")
    public ResponseEntity<UserSummaryResponse> decideKyc(
            @PathVariable String userId,
            @Valid @RequestBody KycDecisionRequest req) {
        return ResponseEntity.ok(userService.decideKyc(userId, req));
    }

    // ─── Hồ sơ doanh nghiệp ─────────────────────────────────────────────────

    /** GET /cms/users/business-profiles?status=&page=&size= — danh sách hồ sơ DN chờ duyệt. */
    @GetMapping("/business-profiles")
    public ResponseEntity<JsonNode> getBusinessProfiles(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(userService.getBusinessProfiles(status, page, size));
    }

    /** GET /cms/users/{userId}/business-profile — chi tiết hồ sơ DN. */
    @GetMapping("/{userId}/business-profile")
    public ResponseEntity<JsonNode> getBusinessProfile(@PathVariable String userId) {
        return ResponseEntity.ok(userService.getBusinessProfile(userId));
    }

    /** POST /cms/users/{userId}/business-profile/decision — duyệt/từ chối hồ sơ DN. */
    @PostMapping("/{userId}/business-profile/decision")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'CUSTOMER_SUPPORT') or hasAuthority('business.decide')")
    public ResponseEntity<Map<String, String>> decideBusinessProfile(
            @PathVariable String userId,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal CmsPrincipal operator) {
        boolean approved = Boolean.TRUE.equals(body.get("approved"));
        String reason = body.get("reason") != null ? String.valueOf(body.get("reason")) : null;
        userService.decideBusinessProfile(userId, approved, reason,
                operator != null ? operator.displayName() : "cms");
        return ResponseEntity.ok(Map.of("message", approved
                ? "Đã duyệt hồ sơ doanh nghiệp" : "Đã từ chối hồ sơ doanh nghiệp"));
    }

    /** POST /cms/users/{userId}/business-profile/analyze — AI đọc GPKD (chỉ tham khảo). */
    @PostMapping("/{userId}/business-profile/analyze")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'CUSTOMER_SUPPORT')")
    public ResponseEntity<JsonNode> analyzeBusinessLicense(@PathVariable String userId) {
        return ResponseEntity.ok(userService.analyzeBusinessLicense(userId));
    }

    @PutMapping("/{userId}/status")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'CUSTOMER_SUPPORT')")
    public ResponseEntity<UserSummaryResponse> updateStatus(
            @PathVariable String userId,
            @Valid @RequestBody UserStatusRequest req) {
        return ResponseEntity.ok(userService.updateStatus(userId, req));
    }

    @PostMapping("/{userId}/reset-password")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'CUSTOMER_SUPPORT')")
    public ResponseEntity<ResetCustomerPasswordResponse> resetPassword(@PathVariable String userId) {
        return ResponseEntity.ok(userService.resetPassword(userId));
    }

    @PostMapping("/{userId}/reset-device")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'CUSTOMER_SUPPORT')")
    public ResponseEntity<Void> resetDevice(@PathVariable String userId) {
        userService.resetDevice(userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{userId}/blacklist")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'CUSTOMER_SUPPORT')")
    public ResponseEntity<UserSummaryResponse> setBlacklist(
            @PathVariable String userId,
            @Valid @RequestBody UserBlacklistRequest request) {
        return ResponseEntity.ok(userService.setBlacklist(userId, request.isBlacklisted(), request.getReason()));
    }
}
