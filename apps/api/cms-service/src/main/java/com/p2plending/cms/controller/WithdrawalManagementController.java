package com.p2plending.cms.controller;

import com.p2plending.cms.dto.response.PagedResponse;
import com.p2plending.cms.dto.response.WithdrawalSummaryResponse;
import com.p2plending.cms.security.CmsPrincipal;
import com.p2plending.cms.service.WithdrawalManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/cms/withdrawals")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'OPS')")
public class WithdrawalManagementController {

    private final WithdrawalManagementService withdrawalManagementService;

    /**
     * Danh sách withdrawal để ops giám sát.
     * statuses: TRANSFER_FAILED, FAILED, TRANSFER_INITIATED, PROCESSING
     * Mặc định (không truyền): trả về TRANSFER_FAILED + FAILED.
     */
    @GetMapping("/monitoring")
    public ResponseEntity<PagedResponse<WithdrawalSummaryResponse>> getForMonitoring(
            @RequestParam(required = false) String statuses,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(withdrawalManagementService.getForMonitoring(
                statuses, Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
    }

    /**
     * Ops retry chuyển tiền thủ công sau TRANSFER_FAILED.
     */
    @PostMapping("/{withdrawalId}/retry")
    public ResponseEntity<Map<String, String>> retry(
            @PathVariable String withdrawalId,
            @AuthenticationPrincipal CmsPrincipal admin) {
        withdrawalManagementService.retry(admin.userId(), withdrawalId);
        return ResponseEntity.ok(Map.of("message", "Đã khởi động lại chuyển tiền."));
    }

    /**
     * Ops resolve giao dịch kẹt ở PROCESSING/TRANSFER_INITIATED — CHỈ sau khi đã xác minh
     * thủ công tại TIKLUY/MB. wasSent=true → đóng là đã chuyển (cần ftNumber);
     * wasSent=false → hoàn tiền về ví.
     */
    @PostMapping("/{withdrawalId}/resolve")
    public ResponseEntity<Map<String, String>> resolve(
            @PathVariable String withdrawalId,
            @RequestBody ResolveRequest req,
            @AuthenticationPrincipal CmsPrincipal admin) {
        withdrawalManagementService.resolve(
                admin.userId(), withdrawalId, req.isWasSent(), req.getFtNumber(), req.getNote());
        return ResponseEntity.ok(Map.of("message",
                req.isWasSent() ? "Đã đóng giao dịch là đã chuyển." : "Đã hoàn tiền về ví."));
    }

    @lombok.Data
    public static class ResolveRequest {
        private boolean wasSent;
        private String ftNumber;
        private String note;
    }
}
