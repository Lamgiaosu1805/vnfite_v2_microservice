package com.p2plending.cms.controller;

import com.p2plending.cms.dto.request.ChangePasswordRequest;
import com.p2plending.cms.dto.request.CreateAdminRequest;
import com.p2plending.cms.dto.request.SetupRequest;
import com.p2plending.cms.dto.response.AdminListResponse;
import com.p2plending.cms.dto.response.CreateAdminResponse;
import com.p2plending.cms.service.AdminManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
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
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ChangePasswordRequest request) {
        // Lấy adminId từ JWT subject (username) → load từ DB để lấy id
        service.changePassword(userDetails.getUsername(), request);
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
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CreateAdminRequest request) {
        CreateAdminResponse response = service.createAdmin(request, userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

    @PutMapping("/admins/{id}/toggle-active")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> toggleActive(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails) {
        service.toggleActive(id, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }
}
