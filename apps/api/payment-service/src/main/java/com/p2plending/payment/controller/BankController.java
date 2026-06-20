package com.p2plending.payment.controller;

import com.p2plending.payment.dto.request.AddBankRequest;
import com.p2plending.payment.dto.response.BankCatalogItem;
import com.p2plending.payment.dto.response.LinkedBankResponse;
import com.p2plending.payment.security.AuthenticatedUser;
import com.p2plending.payment.service.LinkedBankService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payment/banks")
@RequiredArgsConstructor
public class BankController {

    private final LinkedBankService linkedBankService;

    @GetMapping
    public ResponseEntity<List<LinkedBankResponse>> listBanks(
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(linkedBankService.listBanks(user.userId()));
    }

    @PostMapping
    public ResponseEntity<LinkedBankResponse> addBank(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody @Valid AddBankRequest req) {
        return ResponseEntity.ok(linkedBankService.addBank(user.userId(), req));
    }

    @DeleteMapping("/{bankId}")
    public ResponseEntity<Map<String, String>> removeBank(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String bankId) {
        linkedBankService.removeBank(user.userId(), bankId);
        return ResponseEntity.ok(Map.of("message", "Đã xóa tài khoản ngân hàng"));
    }

    /** Danh sách ngân hàng hỗ trợ — proxy từ TIKLUY common/bank */
    @GetMapping("/catalog")
    public ResponseEntity<List<BankCatalogItem>> bankCatalog() {
        return ResponseEntity.ok(linkedBankService.getBankCatalog());
    }

    /** Xác minh tên chủ TK ngân hàng qua MB Bank (trước khi add) */
    @GetMapping("/verify")
    public ResponseEntity<Map<String, String>> verifyBank(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam String bankCode,
            @RequestParam String bankAccountNo) {
        String name = linkedBankService.verifyBankAccount(user.userId(), bankCode, bankAccountNo);
        return ResponseEntity.ok(Map.of("accountName", name));
    }
}
