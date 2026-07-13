package com.p2plending.auth.controller;

import com.p2plending.auth.common.Constant;
import com.p2plending.auth.service.vwork.SyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/sync")
@RequiredArgsConstructor
public class SyncController {
    private final SyncService syncService;

    @PostMapping("/customers")
    public ResponseEntity<Map<String, String>> syncCustomers(@RequestHeader(Constant.TRANSACTION_ID_KEY) String transactionId) {
        return ResponseEntity.ok(syncService.syncAllCustomers(transactionId));
    }
}
