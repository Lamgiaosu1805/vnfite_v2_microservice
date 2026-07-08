package com.p2plending.cms.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.p2plending.cms.security.CmsPrincipal;
import com.p2plending.cms.service.SourceServiceClient;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/cms/reconciliation")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'OPS', 'FINANCE')")
public class ReconciliationController {

    private final SourceServiceClient sourceServiceClient;

    @PostMapping("/run")
    public ResponseEntity<JsonNode> run(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "false") boolean autoFixDeposits,
            @AuthenticationPrincipal CmsPrincipal principal) {
        LocalDate reconDate = date != null ? date : LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
        String runBy = principal != null ? principal.displayName() : "ops";
        return ResponseEntity.ok(sourceServiceClient.runReconciliation(reconDate, runBy, autoFixDeposits));
    }

    @GetMapping("/sessions")
    public ResponseEntity<JsonNode> sessions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(sourceServiceClient.getReconciliationSessions(page, Math.min(size, 50)));
    }

    @GetMapping("/sessions/{sessionId}/items")
    public ResponseEntity<JsonNode> items(
            @PathVariable String sessionId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(sourceServiceClient.getReconciliationItems(sessionId, status, page, Math.min(size, 100)));
    }

    @PutMapping("/items/{itemId}/resolve")
    public ResponseEntity<Void> resolve(
            @PathVariable String itemId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal CmsPrincipal principal) {
        String resolvedBy = principal != null ? principal.displayName() : "ops";
        sourceServiceClient.resolveReconciliationItem(itemId, resolvedBy, body.get("notes"));
        return ResponseEntity.ok().build();
    }

    @PutMapping("/items/{itemId}/investigate")
    public ResponseEntity<Void> investigate(
            @PathVariable String itemId,
            @AuthenticationPrincipal CmsPrincipal principal) {
        String updatedBy = principal != null ? principal.displayName() : "ops";
        sourceServiceClient.markReconciliationItemInvestigating(itemId, updatedBy);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/items/{itemId}/backfill-deposit")
    public ResponseEntity<Void> backfillDeposit(
            @PathVariable String itemId,
            @AuthenticationPrincipal CmsPrincipal principal) {
        String resolvedBy = principal != null ? principal.displayName() : "ops";
        sourceServiceClient.backfillMissingDeposit(itemId, resolvedBy);
        return ResponseEntity.ok().build();
    }
}
