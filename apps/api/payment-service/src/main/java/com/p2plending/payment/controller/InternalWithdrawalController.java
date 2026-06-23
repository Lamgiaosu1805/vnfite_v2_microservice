package com.p2plending.payment.controller;

import com.p2plending.payment.config.AppProperties;
import com.p2plending.payment.domain.entity.LinkedBank;
import com.p2plending.payment.domain.entity.WithdrawalRequest;
import com.p2plending.payment.domain.enums.WithdrawalStatus;
import com.p2plending.payment.domain.repository.LinkedBankRepository;
import com.p2plending.payment.domain.repository.WithdrawalRequestRepository;
import com.p2plending.payment.dto.response.WithdrawalResponse;
import com.p2plending.payment.service.WithdrawalRequestService;
import com.p2plending.payment.service.WithdrawalTransferOrchestrator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Internal endpoints cho luồng rút tiền — nhận từ TIKLUY callback và CMS/Ops.
 * Spring Security để /internal/** = permitAll nên MỌI endpoint ở đây PHẢI tự xác thực:
 *   - Callback TIKLUY: secret callback HOẶC IP allowlist (fail-closed).
 *   - CMS/Ops: X-Internal-Secret khớp app.internal.secret.
 */
@RestController
@RequestMapping("/internal/payment/withdrawal")
@RequiredArgsConstructor
@Slf4j
public class InternalWithdrawalController {

    private final WithdrawalRequestService withdrawalRequestService;
    private final WithdrawalTransferOrchestrator withdrawalTransferOrchestrator;
    private final WithdrawalRequestRepository withdrawalRepository;
    private final LinkedBankRepository linkedBankRepository;
    private final AppProperties appProperties;

    // ─── TIKLUY Callback ─────────────────────────────────────────────────────

    /**
     * TIKLUY gọi khi MB Bank xử lý xong lệnh chuyển tiền.
     * Body: { transferRef, success, ftNumber, errorCode }
     */
    @PostMapping("/transfer-callback")
    public ResponseEntity<Map<String, String>> transferCallback(
            @RequestHeader(value = "X-Internal-Secret", required = false) String internalSecret,
            @RequestHeader(value = "X-Callback-Secret", required = false) String callbackSecret,
            @RequestHeader(value = "X-Forwarded-For", required = false) String forwardedFor,
            HttpServletRequest request,
            @RequestBody TransferCallbackRequest req) {

        if (!isAuthorizedCallback(internalSecret, callbackSecret, forwardedFor, request.getRemoteAddr())) {
            log.error("[MB-CALLBACK] UNAUTHORIZED transferRef={} từ ip={} — từ chối",
                    req.getTransferRef(), resolveClientIp(forwardedFor, request.getRemoteAddr()));
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        // Log toàn bộ payload raw từ MB/TIKLUY — ops cần xem errorCode thật để cấu hình non_retryable_error_codes
        if (req.isSuccess()) {
            log.info("[MB-CALLBACK] SUCCESS transferRef={} ftNumber={}", req.getTransferRef(), req.getFtNumber());
        } else {
            log.warn("[MB-CALLBACK] FAILED transferRef={} errorCode='{}' — nếu lỗi này cần non-retryable, thêm vào DB: UPDATE withdrawal_transfer_configs SET non_retryable_error_codes='{},...' WHERE active=1",
                    req.getTransferRef(), req.getErrorCode(), req.getErrorCode());
        }
        withdrawalTransferOrchestrator.handleTransferCallback(
                req.getTransferRef(), req.isSuccess(),
                req.getFtNumber(), req.getErrorCode());
        return ResponseEntity.ok(Map.of("message", "OK"));
    }

    // ─── CMS/Ops Monitoring ───────────────────────────────────────────────────

    /**
     * CMS/Ops xem danh sách withdrawal đang xử lý hoặc thất bại để monitoring.
     * statuses: TRANSFER_FAILED, FAILED, TRANSFER_INITIATED, PROCESSING (mặc định: TRANSFER_FAILED + FAILED)
     */
    @GetMapping("/monitoring")
    public ResponseEntity<Page<WithdrawalResponse>> getForMonitoring(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret,
            @RequestParam(required = false) Set<WithdrawalStatus> statuses,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (!isValidInternalSecret(secret)) {
            return ResponseEntity.status(401).build();
        }
        Set<WithdrawalStatus> filter = (statuses != null && !statuses.isEmpty())
                ? statuses
                : Set.of(WithdrawalStatus.TRANSFER_FAILED, WithdrawalStatus.FAILED);
        Page<WithdrawalRequest> result = withdrawalRepository
                .findByStatusInAndIsDeletedFalseOrderByCreatedAtDesc(
                        filter, PageRequest.of(page, size, Sort.by("createdAt").descending()));
        return ResponseEntity.ok(result.map(this::toResponse));
    }

    // ─── Ops: retry thủ công (chỉ khi TRANSFER_FAILED / FAILED) ───────────────

    @PostMapping("/{withdrawalId}/retry")
    public ResponseEntity<Map<String, String>> retry(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret,
            @PathVariable String withdrawalId,
            @RequestHeader("X-Admin-Id") String adminId) {
        if (!isValidInternalSecret(secret)) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        withdrawalTransferOrchestrator.retryTransfer(adminId, withdrawalId);
        return ResponseEntity.ok(Map.of("message", "Đã khởi động lại chuyển tiền."));
    }

    /**
     * Ops resolve giao dịch kẹt ở PROCESSING/TRANSFER_INITIATED sau khi ĐÃ xác minh tại TIKLUY/MB.
     * wasSent=true → đóng như thành công (cần ftNumber). wasSent=false → hoàn tiền về ví.
     * Đây là lối thoát duy nhất, có kiểm soát, cho giao dịch mơ hồ — không bao giờ tự động.
     */
    @PostMapping("/{withdrawalId}/resolve")
    public ResponseEntity<Map<String, String>> resolve(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret,
            @PathVariable String withdrawalId,
            @RequestHeader("X-Admin-Id") String adminId,
            @RequestBody ResolveRequest req) {
        if (!isValidInternalSecret(secret)) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        withdrawalRequestService.resolveProcessing(
                adminId, withdrawalId, req.isWasSent(), req.getFtNumber(), req.getNote());
        return ResponseEntity.ok(Map.of("message",
                req.isWasSent() ? "Đã đóng giao dịch là đã chuyển." : "Đã hoàn tiền về ví."));
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private WithdrawalResponse toResponse(WithdrawalRequest wr) {
        Optional<LinkedBank> bank = linkedBankRepository.findByIdAndIsDeletedFalse(wr.getLinkedBankId());
        return WithdrawalResponse.from(wr,
                bank.map(LinkedBank::getBankName).orElse(null),
                bank.map(LinkedBank::getBankAccountNo).orElse(null));
    }

    private boolean isValidInternalSecret(String secret) {
        String expected = appProperties.getInternal().getSecret();
        return expected != null && !expected.isBlank() && expected.equals(secret);
    }

    /**
     * Fail-closed: callback chỉ được chấp nhận nếu khớp một trong:
     *   - X-Internal-Secret == app.internal.secret (gọi nội bộ/relay), hoặc
     *   - X-Callback-Secret == app.tikluy.callback.secret (nếu đã cấu hình), hoặc
     *   - IP nguồn nằm trong app.tikluy.callback.allowedIps (nếu đã cấu hình).
     * Nếu KHÔNG cấu hình secret/allowlist nào → từ chối (buộc ops cấu hình trước khi go-live).
     */
    private boolean isAuthorizedCallback(String internalSecret, String callbackSecret,
                                         String forwardedFor, String remoteAddr) {
        if (isValidInternalSecret(internalSecret)) {
            return true;
        }
        String expectedCallbackSecret = appProperties.getTikluy().getCallback().getSecret();
        if (StringUtils.hasText(expectedCallbackSecret) && expectedCallbackSecret.equals(callbackSecret)) {
            return true;
        }
        String allowedIps = appProperties.getTikluy().getCallback().getAllowedIps();
        if (StringUtils.hasText(allowedIps)) {
            String clientIp = resolveClientIp(forwardedFor, remoteAddr);
            return Arrays.stream(allowedIps.split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .anyMatch(clientIp::equals);
        }
        // Không có credential nào được cấu hình → fail-closed (khác deposit callback fail-open).
        log.error("[MB-CALLBACK] Chưa cấu hình app.tikluy.callback.secret/allowedIps — withdrawal callback bị từ chối. Ops cần cấu hình trước khi go-live.");
        return false;
    }

    private String resolveClientIp(String forwardedFor, String remoteAddr) {
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        return remoteAddr;
    }

    // ─── DTOs ─────────────────────────────────────────────────────────────────

    @Data
    public static class TransferCallbackRequest {
        private String transferRef;
        private boolean success;
        private String ftNumber;
        private String errorCode;
    }

    @Data
    public static class ResolveRequest {
        private boolean wasSent;
        private String ftNumber;
        private String note;
    }
}
