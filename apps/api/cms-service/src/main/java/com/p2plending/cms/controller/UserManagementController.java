package com.p2plending.cms.controller;

import com.p2plending.cms.domain.enums.UserAccountStatus;
import com.p2plending.cms.dto.request.KycDecisionRequest;
import com.p2plending.cms.dto.request.UserStatusRequest;
import com.p2plending.cms.dto.response.CustomerDetailResponse;
import com.p2plending.cms.dto.response.PagedResponse;
import com.p2plending.cms.dto.response.UserSummaryResponse;
import com.p2plending.cms.service.UserManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/cms/users")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'OPS')")
public class UserManagementController {

    private final UserManagementService userService;

    @GetMapping
    public ResponseEntity<PagedResponse<UserSummaryResponse>> getUsers(
            @RequestParam(required = false) String kycStatus,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) UserAccountStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(userService.getUsers(kycStatus, role, status, search, page, size));
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
            @RequestParam(defaultValue = "20") int loanSize) {
        return ResponseEntity.ok(userService.getCustomerDetail(
                userId, transactionPage, transactionSize, loanPage, loanSize));
    }

    @PutMapping("/{userId}/kyc")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<UserSummaryResponse> decideKyc(
            @PathVariable String userId,
            @Valid @RequestBody KycDecisionRequest req) {
        return ResponseEntity.ok(userService.decideKyc(userId, req));
    }

    @PutMapping("/{userId}/status")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<UserSummaryResponse> updateStatus(
            @PathVariable String userId,
            @Valid @RequestBody UserStatusRequest req) {
        return ResponseEntity.ok(userService.updateStatus(userId, req));
    }
}
