package com.p2plending.payment.controller;

import com.p2plending.payment.config.AppProperties;
import com.p2plending.payment.dto.request.AddBankRequest;
import com.p2plending.payment.dto.request.DepositCallbackRequest;
import com.p2plending.payment.dto.response.LinkedBankResponse;
import com.p2plending.payment.dto.response.TransactionResponse;
import com.p2plending.payment.dto.response.WalletResponse;
import com.p2plending.payment.service.LinkedBankService;
import com.p2plending.payment.service.TikluyClient;
import com.p2plending.payment.service.WalletService;
import com.p2plending.payment.service.WithdrawService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Endpoints nội bộ — không expose qua Nginx, bảo vệ bằng X-Internal-Secret header.
 *
 * Gồm 2 nhóm:
 *  1. /internal/payment/...  — dành cho loan-service, cms-service gọi vào
 *  2. /notification/save-by-ms-account — TIKLUY gọi khi có nạp tiền từ MB Bank
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class InternalPaymentController {

    private final WalletService walletService;
    private final WithdrawService withdrawService;
    private final LinkedBankService linkedBankService;
    private final AppProperties appProperties;
    private final TikluyClient tikluyClient;

    // ─── TIKLUY deposit callback ──────────────────────────────────────────────

    /**
     * TIKLUY gọi endpoint này (AppFeignService) khi có giao dịch nạp tiền thành công.
     * Path khớp với AppFeignService.java của TIKLUY: POST /notification/save-by-ms-account
     * Header: transactionId (từ TIKLUY)
     *
     * Cấu hình TIKLUY: spring.vnf-ms.app.url = http://payment-service:8086
     */
    /**
     * Stub endpoint cho TIKLUY CmsFeignService.
     * TIKLUY gọi đây TRƯỚC khi gọi /notification/save-by-ms-account.
     * Không có try-catch bên TIKLUY → phải trả 200 nếu không deposit sẽ fail.
     * Business logic đã được xử lý ở payment-service (wallets), không cần làm gì thêm.
     */
    @PostMapping("/transaction-management/add-transaction-by-ms-account")
    public ResponseEntity<Void> tikluyAddTransactionStub(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret,
            @RequestHeader(value = "X-Forwarded-For", required = false) String forwardedFor,
            @RequestHeader(value = "transactionId", required = false) String txnId,
            @RequestParam(required = false) String bankAccountNumber,
            @RequestParam(required = false) String amount,
            jakarta.servlet.http.HttpServletRequest request) {
        if (!isAllowedTikluyCallback(secret, forwardedFor, request.getRemoteAddr())) {
            return ResponseEntity.status(401).build();
        }
        log.info("txnId={} TIKLUY CMS stub: accNo={} amount={}", txnId, bankAccountNumber, amount);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/notification/save-by-ms-account")
    public ResponseEntity<Map<String, String>> handleDepositCallback(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret,
            @RequestHeader(value = "X-Forwarded-For", required = false) String forwardedFor,
            @RequestHeader(value = "transactionId", required = false) String txnId,
            @RequestBody DepositCallbackRequest req,
            jakarta.servlet.http.HttpServletRequest request) {

        if (!isAllowedTikluyCallback(secret, forwardedFor, request.getRemoteAddr())) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        log.info("txnId={} Deposit callback: accNo={} amount={} category={}",
                txnId, req.getAccountNo(), req.getAmount(), req.getCategory());

        // Chỉ xử lý giao dịch nạp tiền (IN), bỏ qua OUT
        if (!"IN".equalsIgnoreCase(req.getCategory())) {
            return ResponseEntity.ok(Map.of("status", "IGNORED"));
        }

        try {
            String effectiveTxnId = txnId != null && !txnId.isBlank()
                    ? txnId
                    : "tikluy-" + req.getAccountNo() + "-" + req.getAmount() + "-" + System.currentTimeMillis();
            BigDecimal amount = new BigDecimal(req.getAmount().replaceAll("[^0-9.]", ""));
            BigDecimal runningBalance = null;
            if (req.getRunningBalance() != null && !req.getRunningBalance().isBlank()) {
                try { runningBalance = new BigDecimal(req.getRunningBalance().replaceAll("[^0-9.]", "")); }
                catch (NumberFormatException ignored) { }
            }
            walletService.processDeposit(
                    effectiveTxnId,
                    req.getAccountNo(),
                    amount,
                    effectiveTxnId,
                    runningBalance
            );
            return ResponseEntity.ok(Map.of("status", "OK"));
        } catch (Exception e) {
            log.error("txnId={} Error processing deposit callback: {}", txnId, e.getMessage(), e);
            return ResponseEntity.ok(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    // ─── Withdraw callback từ TIKLUY (khi MB xử lý xong) ────────────────────

    /**
     * TIKLUY có thể gọi endpoint này sau khi MB Bank phản hồi kết quả chuyển tiền.
     * (Nếu TIKLUY được cấu hình để forward withdraw result về đây)
     */
    @PostMapping("/internal/payment/withdraw-callback")
    public ResponseEntity<Map<String, String>> handleWithdrawCallback(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret,
            @RequestParam String tikluyTxnId,
            @RequestParam(defaultValue = "true") boolean success) {

        if (!appProperties.getInternal().getSecret().equals(secret)) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        withdrawService.handleWithdrawCallback(tikluyTxnId, success);
        return ResponseEntity.ok(Map.of("status", "OK"));
    }

    // ─── Wallet operations cho loan-service ──────────────────────────────────

    /** loan-service hỏi số dư trước khi cho phép đặt offer */
    @GetMapping("/internal/payment/wallet/{userId}")
    public ResponseEntity<WalletResponse> getWallet(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret,
            @PathVariable String userId) {

        if (!appProperties.getInternal().getSecret().equals(secret)) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(walletService.getWallet(userId));
    }

    /** CMS xem lịch sử biến động ví: cộng/trừ tiền, đầu tư, hoàn tiền, hoàn trả... */
    @GetMapping("/internal/payment/wallet/{userId}/transactions")
    public ResponseEntity<Page<TransactionResponse>> getWalletTransactions(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret,
            @PathVariable String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (!appProperties.getInternal().getSecret().equals(secret)) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(walletService.getTransactions(userId, page, size));
    }

    /** Khóa số tiền khi user đặt offer đầu tư */
    @PostMapping("/internal/payment/wallet/{userId}/lock")
    public ResponseEntity<Map<String, String>> lockAmount(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret,
            @PathVariable String userId,
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String referenceId) {

        if (!appProperties.getInternal().getSecret().equals(secret)) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        walletService.lockAmount(userId, amount, description, referenceId);
        return ResponseEntity.ok(Map.of("status", "OK"));
    }

    /** Mở khóa số tiền khi offer bị từ chối/hủy/hết hạn. referenceId để idempotent (chống hoàn trùng). */
    @PostMapping("/internal/payment/wallet/{userId}/unlock")
    public ResponseEntity<Map<String, String>> unlockAmount(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret,
            @PathVariable String userId,
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String referenceId) {

        if (!appProperties.getInternal().getSecret().equals(secret)) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        walletService.unlockAmount(userId, amount, description, referenceId);
        return ResponseEntity.ok(Map.of("status", "OK"));
    }

    /** Trừ tiền thật khi khoản vay được giải ngân. referenceId để idempotent (chống debit trùng). */
    @PostMapping("/internal/payment/wallet/{userId}/debit")
    public ResponseEntity<Map<String, String>> debitInvestment(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret,
            @PathVariable String userId,
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String referenceId) {

        if (!appProperties.getInternal().getSecret().equals(secret)) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        walletService.debitInvestment(userId, amount, description, referenceId);
        return ResponseEntity.ok(Map.of("status", "OK"));
    }

    /** Người gọi vốn nhận tiền giải ngân vào ví VNF sau khi CMS disburse. referenceId để idempotent. */
    @PostMapping("/internal/payment/wallet/{userId}/credit-disbursement")
    public ResponseEntity<Map<String, String>> creditDisbursement(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret,
            @PathVariable String userId,
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String referenceId) {

        if (!appProperties.getInternal().getSecret().equals(secret)) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        walletService.creditDisbursement(userId, amount, description, referenceId);
        return ResponseEntity.ok(Map.of("status", "OK"));
    }

    /** Nhà đầu tư nhận tiền hoàn trả khi người gọi vốn trả nợ. referenceId để idempotent. */
    @PostMapping("/internal/payment/wallet/{userId}/credit")
    public ResponseEntity<Map<String, String>> creditRepayment(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret,
            @PathVariable String userId,
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String referenceId) {

        if (!appProperties.getInternal().getSecret().equals(secret)) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        walletService.creditRepayment(userId, amount, description, referenceId);
        return ResponseEntity.ok(Map.of("status", "OK"));
    }

    /**
     * Người gọi vốn trả nợ — trừ tiền khỏi ví. referenceId để idempotent (chống trừ trùng).
     * Fail-closed: số dư không đủ → walletService ném IllegalStateException → 409 ở GlobalExceptionHandler.
     */
    @PostMapping("/internal/payment/wallet/{userId}/repay-debit")
    public ResponseEntity<Map<String, String>> debitRepayment(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret,
            @PathVariable String userId,
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String referenceId) {

        if (!appProperties.getInternal().getSecret().equals(secret)) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        walletService.debitRepayment(userId, amount, description, referenceId);
        return ResponseEntity.ok(Map.of("status", "OK"));
    }

    /**
     * Migration: link tài khoản VNF có sẵn từ hệ thống cũ vào payment-service.
     *
     * Dùng khi chuyển từ VNFITE cũ sang VNFITE mới:
     *  - Không tạo tài khoản mới trên TIKLUY (account đã tồn tại)
     *  - Lấy balance hiện tại từ TIKLUY, ghi vào wallets
     *  - Idempotent: nếu ví đã tồn tại thì skip, không báo lỗi
     *
     * Chạy script SQL migration sẽ tiện hơn khi bulk import,
     * endpoint này dùng cho từng user hoặc khi DB khác server.
     */
    @PostMapping("/internal/payment/wallet/link-existing")
    public ResponseEntity<WalletResponse> linkExistingWallet(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret,
            @RequestParam String userId,
            @RequestParam String vnfAccountNo) {

        if (!appProperties.getInternal().getSecret().equals(secret)) {
            return ResponseEntity.status(401).build();
        }

        return ResponseEntity.ok(walletService.linkExistingAccount(userId, vnfAccountNo));
    }

    /** CMS / auth-service gọi khi KYC user được duyệt → tạo ví + VA */
    @PostMapping("/internal/payment/wallet/init")
    public ResponseEntity<WalletResponse> initWallet(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret,
            @RequestParam String userId,
            @RequestParam String fullName,
            @RequestParam String cccdNumber) {

        if (!appProperties.getInternal().getSecret().equals(secret)) {
            return ResponseEntity.status(401).build();
        }

        return ResponseEntity.ok(walletService.createWallet(userId, fullName, cccdNumber));
    }

    /** Lấy danh sách bank đã liên kết (cho CMS xem) */
    @GetMapping("/internal/payment/wallet/{userId}/banks")
    public ResponseEntity<List<LinkedBankResponse>> listBanks(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret,
            @PathVariable String userId) {

        if (!appProperties.getInternal().getSecret().equals(secret)) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(linkedBankService.listBanks(userId));
    }

    /** Mock: thêm tiền trực tiếp vào ví để test (chỉ hoạt động khi mock=true) */
    @PostMapping("/internal/payment/wallet/{userId}/mock-deposit")
    public ResponseEntity<Map<String, Object>> mockDeposit(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret,
            @PathVariable String userId,
            @RequestParam BigDecimal amount) {

        if (!appProperties.getInternal().getSecret().equals(secret)) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        String txnId = "MOCK_DEP_" + System.currentTimeMillis();
        WalletResponse wallet = walletService.getWallet(userId);

        // 1. Cập nhật TOTAL_MONEY trên TIKLUY để số dư live khớp
        tikluyClient.topUpAccount(txnId, wallet.getVnfAccountNo(), amount);

        // 2. Ghi nhận giao dịch vào payment-service (dedup theo txnId)
        walletService.processDeposit(txnId, wallet.getVnfAccountNo(), amount, txnId, amount);

        return ResponseEntity.ok(Map.of(
                "status", "OK",
                "message", "Nạp " + amount + " VND vào ví user " + userId + " (accNo=" + wallet.getVnfAccountNo() + ")"
        ));
    }

    private boolean isValidInternalSecret(String secret) {
        String expected = appProperties.getInternal().getSecret();
        return expected != null && !expected.isBlank() && expected.equals(secret);
    }

    private boolean isAllowedTikluyCallback(String secret, String forwardedFor, String remoteAddr) {
        String callbackSecret = appProperties.getTikluy().getCallback().getSecret();
        if (StringUtils.hasText(callbackSecret) && callbackSecret.equals(secret)) {
            return true;
        }

        String allowedIps = appProperties.getTikluy().getCallback().getAllowedIps();
        if (!StringUtils.hasText(allowedIps)) {
            return true;
        }

        String clientIp = resolveClientIp(forwardedFor, remoteAddr);
        return Arrays.stream(allowedIps.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .anyMatch(clientIp::equals);
    }

    private String resolveClientIp(String forwardedFor, String remoteAddr) {
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        return remoteAddr;
    }
}
