package com.p2plending.payment.controller;

import com.p2plending.payment.config.AppProperties;
import com.p2plending.payment.dto.response.ReconciliationItemResponse;
import com.p2plending.payment.dto.response.ReconciliationSessionResponse;
import com.p2plending.payment.service.ReconciliationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/internal/reconciliation")
@RequiredArgsConstructor
@Slf4j
public class InternalReconciliationController {

    private final ReconciliationService reconciliationService;
    private final AppProperties appProperties;

    private void checkSecret(String secret) {
        if (!appProperties.getInternal().getSecret().equals(secret)) {
            throw new org.springframework.security.access.AccessDeniedException("Invalid internal secret");
        }
    }

    @PostMapping("/run")
    public ResponseEntity<ReconciliationSessionResponse> runReconciliation(
            @RequestHeader("X-Internal-Secret") String secret,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "system") String runBy) {
        checkSecret(secret);
        LocalDate reconDate = date != null ? date : LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
        log.info("Reconciliation triggered by={} date={}", runBy, reconDate);
        var session = reconciliationService.runReconciliation(reconDate, runBy);
        return ResponseEntity.ok(ReconciliationSessionResponse.from(session));
    }

    @GetMapping("/sessions")
    public ResponseEntity<Page<ReconciliationSessionResponse>> listSessions(
            @RequestHeader("X-Internal-Secret") String secret,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        checkSecret(secret);
        return ResponseEntity.ok(
                reconciliationService.listSessions(page, Math.min(size, 50))
                        .map(ReconciliationSessionResponse::from));
    }

    @GetMapping("/sessions/{sessionId}/items")
    public ResponseEntity<Page<ReconciliationItemResponse>> listItems(
            @RequestHeader("X-Internal-Secret") String secret,
            @PathVariable String sessionId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        checkSecret(secret);
        return ResponseEntity.ok(
                reconciliationService.listItems(sessionId, status, page, Math.min(size, 100))
                        .map(ReconciliationItemResponse::from));
    }

    @PutMapping("/items/{itemId}/resolve")
    public ResponseEntity<Void> resolveItem(
            @RequestHeader("X-Internal-Secret") String secret,
            @PathVariable String itemId,
            @RequestBody Map<String, String> body) {
        checkSecret(secret);
        reconciliationService.resolveItem(itemId, body.getOrDefault("resolvedBy", "ops"), body.get("notes"));
        return ResponseEntity.ok().build();
    }

    @PutMapping("/items/{itemId}/investigate")
    public ResponseEntity<Void> markInvestigating(
            @RequestHeader("X-Internal-Secret") String secret,
            @PathVariable String itemId,
            @RequestBody Map<String, String> body) {
        checkSecret(secret);
        reconciliationService.markItemInvestigating(itemId, body.getOrDefault("updatedBy", "ops"));
        return ResponseEntity.ok().build();
    }
}
