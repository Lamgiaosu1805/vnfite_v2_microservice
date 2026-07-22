package com.p2plending.cms.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.p2plending.cms.security.CmsPrincipal;
import com.p2plending.cms.service.CounterCoreManagementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/cms/counter-management")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
public class CounterManagementController {

    private final CounterCoreManagementService managementService;

    @GetMapping("/branches")
    public ResponseEntity<JsonNode> branches(@AuthenticationPrincipal CmsPrincipal operator) {
        return ResponseEntity.ok(managementService.listBranches(operator));
    }

    @PostMapping("/branches")
    public ResponseEntity<JsonNode> createBranch(@Valid @RequestBody CreateBranchBody body,
                                                  @AuthenticationPrincipal CmsPrincipal operator) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(managementService.createBranch(body, operator));
    }

    @GetMapping("/staff")
    public ResponseEntity<JsonNode> staff(@AuthenticationPrincipal CmsPrincipal operator) {
        return ResponseEntity.ok(managementService.listStaff(operator));
    }

    @PostMapping("/staff")
    public ResponseEntity<JsonNode> createStaff(@Valid @RequestBody CreateStaffBody body,
                                                 @AuthenticationPrincipal CmsPrincipal operator) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(managementService.createStaff(body, operator));
    }

    public record CreateBranchBody(
            @NotBlank @Size(max = 30) String branchCode,
            @NotBlank @Size(max = 200) String branchName,
            @NotBlank @Size(max = 500) String address) {}

    public record CreateStaffBody(
            @NotBlank @Pattern(regexp = "^[a-zA-Z0-9._-]+$") @Size(max = 80) String username,
            @NotBlank @Size(min = 12, max = 200) String temporaryPassword,
            @NotBlank @Size(max = 50) String employeeCode,
            @NotBlank @Size(max = 200) String fullName,
            @NotBlank @Pattern(regexp = "TELLER|SUPERVISOR") String role,
            @NotBlank String branchId) {}
}
