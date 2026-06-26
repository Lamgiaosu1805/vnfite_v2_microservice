package com.p2plending.cms.controller;

import com.p2plending.cms.dto.request.ChangePasswordRequest;
import com.p2plending.cms.dto.request.CreateAdminRequest;
import com.p2plending.cms.dto.request.SetupRequest;
import com.p2plending.cms.dto.request.UpdateAdminRoleRequest;
import com.p2plending.cms.dto.response.AdminListResponse;
import com.p2plending.cms.dto.response.CreateAdminResponse;
import com.p2plending.cms.dto.response.ResetAdminPasswordResponse;
import com.p2plending.cms.security.CmsPrincipal;
import com.p2plending.cms.service.AdminManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/cms")
@RequiredArgsConstructor
public class AdminManagementController {

    private final AdminManagementService service;

    // ─── Setup (no auth — chỉ hoạt động khi chưa có admin nào) ──────────────

    @GetMapping("/auth/setup/status")
    public ResponseEntity<Map<String, Boolean>> setupStatus() {
        return ResponseEntity.ok(Map.of("setupRequired", service.isSetupRequired()));
    }

    @PostMapping("/auth/setup")
    public ResponseEntity<Void> setup(@Valid @RequestBody SetupRequest request) {
        service.setup(request);
        return ResponseEntity.noContent().build();
    }

    // ─── Change password (chính mình đổi) ────────────────────────────────────

    @PostMapping("/auth/change-password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal CmsPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request) {
        service.changePassword(principal.username(), request);
        return ResponseEntity.noContent().build();
    }

    // ─── Admin management (SUPER_ADMIN only) ─────────────────────────────────

    @GetMapping("/admins")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<AdminListResponse>> listAdmins() {
        return ResponseEntity.ok(service.listAdmins());
    }

    @PostMapping("/admins")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<CreateAdminResponse> createAdmin(
            @AuthenticationPrincipal CmsPrincipal principal,
            @Valid @RequestBody CreateAdminRequest request) {
        CreateAdminResponse response = service.createAdmin(request, principal.userId());
        return ResponseEntity.ok(response);
    }

    @PutMapping("/admins/{id}/toggle-active")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> toggleActive(
            @PathVariable String id,
            @AuthenticationPrincipal CmsPrincipal principal) {
        service.toggleActive(id, principal.userId());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/admins/{id}/role")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<AdminListResponse> updateRole(
            @PathVariable String id,
            @AuthenticationPrincipal CmsPrincipal principal,
            @Valid @RequestBody UpdateAdminRoleRequest request) {
        return ResponseEntity.ok(service.updateRole(id, request.getRole(), principal.userId()));
    }

    @PostMapping("/admins/{id}/reset-password")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ResetAdminPasswordResponse> resetPassword(
            @PathVariable String id,
            @AuthenticationPrincipal CmsPrincipal principal) {
        return ResponseEntity.ok(service.resetPassword(id, principal.userId()));
    }

    @PostMapping("/admins/{id}/reset-totp")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> resetTotp(
            @PathVariable String id,
            @AuthenticationPrincipal CmsPrincipal principal) {
        service.resetTotp(id, principal.userId());
        return ResponseEntity.noContent().build();
    }
}
