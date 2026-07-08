package com.p2plending.cms.controller;

import com.p2plending.cms.dto.response.PagedResponse;
import com.p2plending.cms.dto.response.SystemTransactionSummaryResponse;
import com.p2plending.cms.service.PaymentManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/cms/transactions")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'OPS', 'FINANCE')")
public class PaymentManagementController {

    private final PaymentManagementService paymentManagementService;

    @GetMapping
    public ResponseEntity<PagedResponse<SystemTransactionSummaryResponse>> getTransactions(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(paymentManagementService.getTransactions(
                type, status, from, to, search, Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
    }
}
