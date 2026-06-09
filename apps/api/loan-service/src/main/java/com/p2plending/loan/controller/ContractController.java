package com.p2plending.loan.controller;

import com.p2plending.loan.dto.request.ContractSignRequest;
import com.p2plending.loan.dto.response.ContractResponse;
import com.p2plending.loan.dto.response.ContractSignInitResponse;
import com.p2plending.loan.security.AuthenticatedUser;
import com.p2plending.loan.service.ContractService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Hợp đồng điện tử của người dùng (nhà đầu tư + người gọi vốn).
 * Ký bằng OTP (mock VNPT eContract).
 */
@RestController
@RequestMapping("/api/contracts")
@RequiredArgsConstructor
public class ContractController {

    private final ContractService contractService;

    /** GET /api/contracts/my?loanId=... — danh sách hợp đồng của tôi (lọc theo khoản nếu có). */
    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ContractResponse>> getMyContracts(
            @RequestParam(required = false) String loanId,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(contractService.getMyContracts(principal.userId(), loanId));
    }

    /** GET /api/contracts/{id} — chi tiết hợp đồng. */
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ContractResponse> getContract(
            @PathVariable String id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(contractService.getContract(id, principal.userId()));
    }

    /** POST /api/contracts/{id}/sign/init — gửi OTP để ký. */
    @PostMapping("/{id}/sign/init")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ContractSignInitResponse> initSign(
            @PathVariable String id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(contractService.initSign(id, principal.userId()));
    }

    /** POST /api/contracts/{id}/sign — xác thực OTP và ký hợp đồng. */
    @PostMapping("/{id}/sign")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ContractResponse> sign(
            @PathVariable String id,
            @Valid @RequestBody ContractSignRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(contractService.signContract(id, principal.userId(), request.getOtp()));
    }
}
