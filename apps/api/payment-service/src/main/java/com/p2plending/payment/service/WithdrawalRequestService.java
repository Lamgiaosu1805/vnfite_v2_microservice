package com.p2plending.payment.service;

import com.p2plending.payment.config.AppProperties;
import com.p2plending.payment.domain.entity.*;
import com.p2plending.payment.domain.enums.TransactionStatus;
import com.p2plending.payment.domain.enums.TransactionType;
import com.p2plending.payment.domain.enums.WithdrawalStatus;
import com.p2plending.payment.domain.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Luồng rút tiền tự động — state machine, idempotent, audit trail đầy đủ.
 *
 * INITIATED → OTP_PENDING → FUNDS_LOCKED → TRANSFER_INITIATED → PROCESSING → COMPLETED
 *                                                              ↘ TRANSFER_FAILED → retry → TRANSFER_INITIATED
 *                                                                                 → FAILED → FUNDS_RELEASED
 * INITIATED/OTP_PENDING → CANCELLED (user tự huỷ trước khi lock tiền)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WithdrawalRequestService {

    private final WalletService walletService;
    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final LinkedBankRepository linkedBankRepository;
    private final WithdrawalRequestRepository withdrawalRepository;
    private final WithdrawalAuditLogRepository auditLogRepository;
    private final WithdrawalTransferConfigRepository configRepository;
    private final TikluyClient tikluyClient;
    private final StringRedisTemplate redisTemplate;
    private final AppProperties appProperties;

    private static final String OTP_KEY = "withdrawal_otp:";
    private static final long OTP_TTL_MINUTES = 5;
    /** Withdrawal kẹt ở TRANSFER_INITIATED / PROCESSING quá thời gian này → reconcile */
    private static final long STUCK_THRESHOLD_MINUTES = 30;

    // ─── Bước 1: Tạo withdrawal request, gửi OTP ─────────────────────────────

    @Transactional
    public WithdrawalRequest initiate(String userId, BigDecimal amount, String linkedBankId) {
        WithdrawalTransferConfig cfg = activeConfig();

        // Một user chỉ được có 1 withdrawal active tại một thời điểm
        if (withdrawalRepository.existsByUserIdAndStatusInAndIsDeletedFalse(
                userId, WithdrawalStatus.ACTIVE)) {
            throw new IllegalStateException(
                    "Ngài đang có một yêu cầu rút tiền đang xử lý. Vui lòng hoàn tất hoặc thử lại sau.");
        }

        // Validate amount
        if (amount.compareTo(BigDecimal.valueOf(10_000)) < 0) {
            throw new IllegalArgumentException("Số tiền rút tối thiểu 10.000 VND.");
        }
        if (amount.compareTo(cfg.getMaxPerTxn()) > 0) {
            throw new IllegalArgumentException(
                    "Số tiền rút vượt giới hạn tối đa " + cfg.getMaxPerTxn().toPlainString() + " VND/giao dịch.");
        }

        // Kiểm tra hạn mức ngày
        LocalDateTime startOfDay = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"))
                .with(LocalTime.MIDNIGHT);
        long todayCount = withdrawalRepository.countCompletedToday(userId, startOfDay);
        if (todayCount >= cfg.getMaxDailyCount()) {
            throw new IllegalStateException(
                    "Đã đạt giới hạn " + cfg.getMaxDailyCount() + " lần rút tiền trong ngày.");
        }
        BigDecimal todayTotal = withdrawalRepository.sumCompletedAmountToday(userId, startOfDay);
        if (todayTotal.add(amount).compareTo(cfg.getMaxDailyTotal()) > 0) {
            throw new IllegalStateException(
                    "Số tiền rút vượt hạn mức ngày " + cfg.getMaxDailyTotal().toPlainString() + " VND.");
        }

        // Validate tài khoản ngân hàng
        LinkedBank bank = linkedBankRepository
                .findByIdAndUserIdAndIsDeletedFalse(linkedBankId, userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Tài khoản ngân hàng không tồn tại hoặc không thuộc về Ngài."));

        // Kiểm tra số dư
        Wallet wallet = walletService.findByUser(userId);
        BigDecimal available = walletService.computeAvailable(wallet);
        if (available.compareTo(amount) < 0) {
            throw new IllegalStateException(
                    "Số dư khả dụng không đủ. Khả dụng: " + available.toPlainString()
                            + " VND, yêu cầu: " + amount.toPlainString() + " VND.");
        }

        // Tạo withdrawal request
        WithdrawalRequest wr = withdrawalRepository.save(WithdrawalRequest.builder()
                .userId(userId)
                .walletId(wallet.getId())
                .linkedBankId(bank.getId())
                .amount(amount)
                .status(WithdrawalStatus.INITIATED)
                .maxRetries(cfg.getMaxRetries())
                .build());

        audit(wr, null, WithdrawalStatus.INITIATED, "USER", userId, "Tạo yêu cầu rút tiền");

        sendOtp(wr.getId(), userId);
        transition(wr, WithdrawalStatus.OTP_PENDING, "USER", userId, "Đã gửi OTP");

        log.info("withdrawal.initiate withdrawalId={} userId={} amount={}", wr.getId(), userId, amount);
        return wr;
    }

    // ─── Bước 2: Xác nhận OTP, lock tiền, chuyển tiền ngay ──────────────────

    @Transactional
    public WithdrawalRequest confirmOtp(String userId, String withdrawalId, String otp) {
        WithdrawalRequest wr = withdrawalRepository
                .findByIdAndUserIdAndIsDeletedFalse(withdrawalId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Yêu cầu rút tiền không tồn tại."));

        if (wr.getStatus() != WithdrawalStatus.OTP_PENDING) {
            throw new IllegalStateException(
                    "Trạng thái không hợp lệ để xác nhận OTP: " + wr.getStatus());
        }

        // Xác thực OTP
        String ns = appProperties.getRedis().getNamespace();
        String otpKey = ns + ":" + OTP_KEY + withdrawalId;
        String storedOtp = redisTemplate.opsForValue().get(otpKey);
        if (storedOtp == null) {
            throw new IllegalStateException("OTP đã hết hạn. Vui lòng tạo lại yêu cầu rút tiền.");
        }
        if (!storedOtp.equals(otp)) {
            throw new IllegalStateException("OTP không chính xác.");
        }
        redisTemplate.delete(otpKey);

        // Khóa ví bằng PESSIMISTIC_WRITE — cùng cơ chế với luồng đầu tư (lockAmount),
        // để rút tiền và đầu tư cùng nối hàng đợi khóa, chống lost-update làm lệch lockedBalance.
        Wallet wallet = walletRepository.findWithLockByUserIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new IllegalStateException("Wallet không tồn tại."));

        // Re-check DƯỚI KHÓA: không cho 2 lệnh rút của cùng user cùng đi qua bước lock tiền
        // (chặn race của check "1 active" ở initiate vốn không có ràng buộc DB).
        if (withdrawalRepository.existsByUserIdAndStatusInAndIsDeletedFalse(
                userId, Set.of(WithdrawalStatus.FUNDS_LOCKED,
                        WithdrawalStatus.TRANSFER_INITIATED,
                        WithdrawalStatus.PROCESSING))) {
            throw new IllegalStateException("Đã có một lệnh rút tiền khác đang được xử lý.");
        }

        // Kiểm tra lại số dư khả dụng tại thời điểm này (dưới khóa)
        BigDecimal available = walletService.computeAvailable(wallet);
        if (available.compareTo(wr.getAmount()) < 0) {
            throw new IllegalStateException(
                    "Số dư đã thay đổi. Khả dụng: " + available.toPlainString() + " VND.");
        }
        wallet.setLockedBalance(wallet.getLockedBalance().add(wr.getAmount()));
        walletRepository.save(wallet);

        // Tạo WalletTransaction PENDING để hiện trong lịch sử giao dịch
        WalletTransaction txn = walletTransactionRepository.save(WalletTransaction.builder()
                .walletId(wallet.getId())
                .type(TransactionType.WITHDRAW)
                .amount(wr.getAmount())
                .status(TransactionStatus.PENDING)
                .description("Rút tiền đang xử lý")
                .build());
        wr.setWalletTxnId(txn.getId());

        transition(wr, WithdrawalStatus.FUNDS_LOCKED, "USER", userId,
                "OTP xác nhận, đã lock " + wr.getAmount() + " VND");

        // Chuyển tiền ngay lập tức
        initiateTransfer(wr, "SYSTEM");

        return wr;
    }

    // ─── Huỷ (chỉ được khi chưa lock tiền) ──────────────────────────────────

    @Transactional
    public void cancel(String userId, String withdrawalId) {
        WithdrawalRequest wr = withdrawalRepository
                .findByIdAndUserIdAndIsDeletedFalse(withdrawalId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Yêu cầu rút tiền không tồn tại."));

        if (wr.getStatus() != WithdrawalStatus.INITIATED
                && wr.getStatus() != WithdrawalStatus.OTP_PENDING) {
            throw new IllegalStateException(
                    "Không thể huỷ sau khi tiền đã được lock. Liên hệ hỗ trợ nếu cần.");
        }

        String ns = appProperties.getRedis().getNamespace();
        redisTemplate.delete(ns + ":" + OTP_KEY + withdrawalId);
        transition(wr, WithdrawalStatus.CANCELLED, "USER", userId, "User tự huỷ");
        log.info("withdrawal.cancelled withdrawalId={} userId={}", withdrawalId, userId);
    }

    // ─── TIKLUY callback ─────────────────────────────────────────────────────

    @Transactional
    public void handleTransferCallback(String transferRef, boolean success,
                                       String ftNumber, String errorCode) {
        WithdrawalRequest wr = withdrawalRepository
                .findByTransferRefAndIsDeletedFalse(transferRef)
                .orElse(null);

        if (wr == null) {
            log.warn("withdrawal.callback.notFound transferRef={}", transferRef);
            return;
        }

        if (wr.getStatus() != WithdrawalStatus.PROCESSING
                && wr.getStatus() != WithdrawalStatus.TRANSFER_INITIATED) {
            log.warn("withdrawal.callback.skipped withdrawalId={} status={}", wr.getId(), wr.getStatus());
            return;
        }

        if (success) {
            wr.setMbFtNumber(ftNumber);
            transition(wr, WithdrawalStatus.COMPLETED, "SYSTEM", "TIKLUY",
                    "Chuyển tiền thành công. FT=" + ftNumber);
            // TIKLUY/MB đã trừ tiền thật khỏi VA → giải phóng phần đã khóa (tiền đã rời ví).
            // Nếu KHÔNG bỏ lock ở đây, lockedBalance phình vĩnh viễn → tiền nạp sau bị "vô hình".
            releaseLock(wr.getUserId(), wr.getAmount());
            updateWalletTxnStatus(wr, TransactionStatus.SUCCESS);
            log.info("withdrawal.completed withdrawalId={} transferRef={} FT={}", wr.getId(), transferRef, ftNumber);
        } else {
            WithdrawalTransferConfig cfg = activeConfig();
            String note = "Chuyển tiền thất bại. errorCode=" + errorCode
                    + " retry=" + wr.getRetryCount() + "/" + wr.getMaxRetries();
            transition(wr, WithdrawalStatus.TRANSFER_FAILED, "SYSTEM", "TIKLUY", note);
            wr.setFailureReason("errorCode=" + errorCode);

            // Log rõ error code raw từ MB để ops biết cần thêm code nào vào non_retryable_error_codes
            log.warn("[MB-ERROR] withdrawalId={} MB_ERROR_CODE='{}' amount={} userId={} retryCount={}/{}",
                    wr.getId(), errorCode, wr.getAmount(), wr.getUserId(),
                    wr.getRetryCount(), wr.getMaxRetries());

            // Nếu error code thuộc danh sách non-retryable (ops cấu hình trong DB)
            // → fail ngay, hoàn tiền, không retry
            if (cfg.isNonRetryable(errorCode)) {
                log.error("[MB-ERROR] NON-RETRYABLE withdrawalId={} errorCode='{}' amount={} — hoàn tiền về ví, ops cần kiểm tra tài khoản chi VNFITE",
                        wr.getId(), errorCode, wr.getAmount());
                markFailed(wr, "Lỗi không thể retry (MB errorCode=" + errorCode + "). Tiền đã hoàn về ví.");
                return;
            }

            if (wr.canRetry()) {
                wr.setRetryCount(wr.getRetryCount() + 1);
                withdrawalRepository.save(wr);
                log.info("withdrawal.retry withdrawalId={} attempt={}/{}", wr.getId(), wr.getRetryCount(), wr.getMaxRetries());
                initiateTransfer(wr, "SYSTEM");
            } else {
                log.error("[MB-ERROR] MAX-RETRY-EXCEEDED withdrawalId={} lastErrorCode='{}' amount={} — hoàn tiền về ví",
                        wr.getId(), errorCode, wr.getAmount());
                markFailed(wr, "Transfer thất bại sau " + wr.getMaxRetries() + " lần thử. errorCode=" + errorCode);
            }
        }
    }

    // ─── Ops: retry thủ công (sau khi stuck hoặc TRANSFER_FAILED) ────────────

    @Transactional
    public void retryTransfer(String adminId, String withdrawalId) {
        WithdrawalRequest wr = withdrawalRepository
                .findByIdAndIsDeletedFalse(withdrawalId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy yêu cầu rút tiền."));

        if (wr.getStatus() != WithdrawalStatus.TRANSFER_FAILED
                && wr.getStatus() != WithdrawalStatus.FAILED) {
            throw new IllegalStateException(
                    "Chỉ có thể retry khi trạng thái là TRANSFER_FAILED hoặc FAILED.");
        }
        if (!wr.canRetry()) {
            throw new IllegalStateException(
                    "Đã đạt giới hạn retry (" + wr.getMaxRetries() + "). Cần reset maxRetries trước.");
        }

        // Tăng retryCount → txnId mới (withdrawalId + "-R" + retryCount) → TIKLUY không dedup với lần cũ.
        // Xóa transferRef cũ để write-ahead ghi ref mới khi gọi TIKLUY.
        // Ops cần xác nhận bên TIKLUY/MB rằng lần trước CHƯA chuyển tiền trước khi nhấn retry.
        wr.setRetryCount(wr.getRetryCount() + 1);
        wr.setTransferRef(null);
        withdrawalRepository.save(wr);
        audit(wr, wr.getStatus(), WithdrawalStatus.TRANSFER_INITIATED,
                "ADMIN", adminId, "Ops retry thủ công lần " + wr.getRetryCount());
        initiateTransfer(wr, adminId);
        log.info("withdrawal.manualRetry withdrawalId={} adminId={} attempt={}/{} newTxnId={}",
                withdrawalId, adminId, wr.getRetryCount(), wr.getMaxRetries(),
                wr.getId() + "-R" + wr.getRetryCount());
    }

    // ─── Ops: resolve giao dịch kẹt ở PROCESSING/TRANSFER_INITIATED ──────────

    /**
     * Sau khi ops ĐÃ xác minh thủ công tại TIKLUY/MB, đóng giao dịch mơ hồ.
     * wasSent=true  → tiền đã chuyển thật → COMPLETED + giải phóng lock (KHÔNG gọi TIKLUY lại).
     * wasSent=false → tiền chưa chuyển     → TRANSFER_FAILED → FAILED → hoàn tiền về ví.
     * Đây là lối thoát DUY NHẤT cho giao dịch mơ hồ; không bao giờ tự động để tránh chi 2 lần.
     */
    @Transactional
    public void resolveProcessing(String adminId, String withdrawalId,
                                  boolean wasSent, String ftNumber, String note) {
        WithdrawalRequest wr = withdrawalRepository
                .findByIdAndIsDeletedFalse(withdrawalId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy yêu cầu rút tiền."));

        if (wr.getStatus() != WithdrawalStatus.PROCESSING
                && wr.getStatus() != WithdrawalStatus.TRANSFER_INITIATED) {
            throw new IllegalStateException(
                    "Chỉ resolve được giao dịch đang ở PROCESSING/TRANSFER_INITIATED. Hiện tại: " + wr.getStatus());
        }

        if (wasSent) {
            wr.setMbFtNumber(ftNumber);
            transition(wr, WithdrawalStatus.COMPLETED, "ADMIN", adminId,
                    "Ops xác minh ĐÃ chuyển. FT=" + ftNumber + (note != null ? ". " + note : ""));
            releaseLock(wr.getUserId(), wr.getAmount());
            updateWalletTxnStatus(wr, TransactionStatus.SUCCESS);
            log.warn("withdrawal.resolved.sent withdrawalId={} adminId={} FT={}", wr.getId(), adminId, ftNumber);
        } else {
            transition(wr, WithdrawalStatus.TRANSFER_FAILED, "ADMIN", adminId,
                    "Ops xác minh CHƯA chuyển" + (note != null ? ". " + note : ""));
            markFailed(wr, "Ops xác minh chưa chuyển — hoàn tiền về ví.");
            log.warn("withdrawal.resolved.notSent withdrawalId={} adminId={}", wr.getId(), adminId);
        }
    }

    // ─── Reconciliation scheduler — xử lý giao dịch kẹt ─────────────────────

    /**
     * Chạy mỗi 5 phút. Tìm các withdrawal kẹt ở TRANSFER_INITIATED / PROCESSING
     * quá {@value STUCK_THRESHOLD_MINUTES} phút mà không nhận được callback,
     * đẩy về TRANSFER_FAILED để tự retry hoặc chờ ops xử lý.
     */
    @Scheduled(fixedDelay = 5 * 60 * 1000)
    @Transactional
    public void reconcileStuck() {
        LocalDateTime threshold = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"))
                .minusMinutes(STUCK_THRESHOLD_MINUTES);

        List<WithdrawalRequest> stuck = withdrawalRepository
                .findStuckWithdrawals(List.of(
                        WithdrawalStatus.TRANSFER_INITIATED,
                        WithdrawalStatus.PROCESSING), threshold);

        if (stuck.isEmpty()) return;

        log.warn("withdrawal.reconcile found {} stuck withdrawal(s)", stuck.size());

        for (WithdrawalRequest wr : stuck) {
            try {
                processStuck(wr);
            } catch (Exception ex) {
                log.error("withdrawal.reconcile error withdrawalId={}: {}", wr.getId(), ex.getMessage(), ex);
            }
        }
    }

    private void processStuck(WithdrawalRequest wr) {
        log.warn("withdrawal.stuck withdrawalId={} status={} transferRef={} amount={} updatedAt={}",
                wr.getId(), wr.getStatus(), wr.getTransferRef(), wr.getAmount(), wr.getUpdatedAt());

        // NẾU transferRef đã được ghi (write-ahead) → TIKLUY đã nhận lệnh.
        // Không được retry vì có thể TIKLUY đang xử lý hoặc callback chưa về.
        // Chỉ alert ops để kiểm tra trạng thái bên TIKLUY/MB rồi quyết định.
        if (wr.getTransferRef() != null) {
            log.error("[RECONCILE-STUCK] withdrawalId={} transferRef={} amount={} — TIKLUY đã nhận lệnh, chưa có callback. " +
                      "KHÔNG tự retry. Ops cần kiểm tra bên TIKLUY/MB rồi dùng /retry nếu chắc chắn chưa chuyển.",
                    wr.getId(), wr.getTransferRef(), wr.getAmount());
            // Không làm gì thêm — chờ callback hoặc ops can thiệp
            return;
        }

        // transferRef = null → lệnh chưa gửi được sang TIKLUY → an toàn để retry
        transition(wr, WithdrawalStatus.TRANSFER_FAILED, "SYSTEM", "RECONCILER",
                "Kẹt quá " + STUCK_THRESHOLD_MINUTES + " phút, chưa gửi được lệnh sang TIKLUY");

        if (wr.canRetry()) {
            wr.setRetryCount(wr.getRetryCount() + 1);
            withdrawalRepository.save(wr);
            log.info("withdrawal.reconcile.retry withdrawalId={} attempt={}/{}", wr.getId(), wr.getRetryCount(), wr.getMaxRetries());
            initiateTransfer(wr, "RECONCILER");
        } else {
            markFailed(wr, "Kẹt quá " + STUCK_THRESHOLD_MINUTES + " phút, chưa gửi sang TIKLUY, hết lần retry");
        }
    }

    // ─── Query ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public WithdrawalRequest getForUser(String userId, String withdrawalId) {
        return withdrawalRepository
                .findByIdAndUserIdAndIsDeletedFalse(withdrawalId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy yêu cầu rút tiền."));
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    /**
     * Thử parse error code từ exception message.
     * TIKLUY có thể ném RuntimeException("INSUFFICIENT_FUNDS: ...") hoặc tương tự.
     */
    private String extractErrorCode(String message) {
        if (message == null) return null;
        int colon = message.indexOf(':');
        String candidate = (colon > 0 ? message.substring(0, colon) : message).trim().toUpperCase();
        // Chỉ trả về nếu trông giống error code (chữ hoa + gạch dưới, max 50 ký tự)
        return candidate.matches("[A-Z][A-Z0-9_]{1,49}") ? candidate : null;
    }

    private void initiateTransfer(WithdrawalRequest wr, String actor) {
        transition(wr, WithdrawalStatus.TRANSFER_INITIATED, "SYSTEM", actor,
                "Bắt đầu chuyển tiền qua TIKLUY");

        WithdrawalTransferConfig cfg = activeConfig();
        Wallet wallet = walletRepository.findById(wr.getWalletId())
                .orElseThrow(() -> new IllegalStateException("Wallet không tồn tại."));
        LinkedBank bank = linkedBankRepository.findByIdAndIsDeletedFalse(wr.getLinkedBankId())
                .orElseThrow(() -> new IllegalStateException("Tài khoản ngân hàng không còn hợp lệ."));

        // Idempotency: txnId deterministc theo withdrawalId + retryCount.
        // Cùng một lần thử luôn dùng cùng txnId → TIKLUY dedup nếu nhận trùng.
        String txnId = wr.getId() + "-R" + wr.getRetryCount();

        // Write-ahead: lưu transferRef vào DB TRƯỚC khi gọi TIKLUY.
        // Nếu sau đó bị timeout, ta biết TIKLUY đã nhận lệnh → không retry mù.
        wr.setTransferRef(txnId);
        withdrawalRepository.save(wr);

        if (appProperties.getPayment().isMock()) {
            String mockFt = "MOCK_FT_" + wr.getId().substring(0, 8);
            transition(wr, WithdrawalStatus.PROCESSING, "SYSTEM", "SYSTEM", "Mock transfer initiated");
            handleTransferCallback(txnId, true, mockFt, null);
            return;
        }

        try {
            String externalRef = tikluyClient.fundTransfer(
                    txnId,
                    wallet.getVnfAccountNo(),
                    bank.getBankCode(),
                    bank.getBankAccountNo(),
                    wr.getAmount().toPlainString());

            // Cập nhật ref thật từ TIKLUY nếu khác với txnId ta đã lưu
            if (externalRef != null && !externalRef.equals(txnId)) {
                wr.setTransferRef(externalRef);
                withdrawalRepository.save(wr);
            }
            transition(wr, WithdrawalStatus.PROCESSING, "SYSTEM", "SYSTEM",
                    "TIKLUY nhận lệnh. ref=" + wr.getTransferRef());

            log.info("withdrawal.tikluy.sent withdrawalId={} txnId={} ref={} amount={}",
                    wr.getId(), txnId, wr.getTransferRef(), wr.getAmount());

        } catch (Exception e) {
            String errorMsg = e.getMessage();
            wr.setFailureReason(errorMsg);

            // ─── PHÂN LOẠI LỖI để KHÔNG BAO GIỜ chuyển tiền 2 lần ───
            // Lỗi MƠ HỒ (read-timeout, 5xx, lỗi không rõ): TIKLUY CÓ THỂ đã nhận & xử lý lệnh.
            // → Tuyệt đối KHÔNG tự retry, KHÔNG hoàn tiền. Giữ khóa + transferRef, đẩy về PROCESSING
            //   để chờ callback thật hoặc reconciler + ops xác minh tại TIKLUY/MB.
            if (!isDefinitelyNotSent(e)) {
                transition(wr, WithdrawalStatus.PROCESSING, "SYSTEM", "SYSTEM",
                        "TIKLUY timeout/không rõ kết quả — chờ callback/đối soát, KHÔNG tự retry: " + errorMsg);
                log.error("[TRANSFER-AMBIGUOUS] withdrawalId={} transferRef={} amount={} userId={} — " +
                          "có thể đã gửi sang MB. Giữ khóa tiền, chờ callback hoặc ops đối soát thủ công.",
                        wr.getId(), wr.getTransferRef(), wr.getAmount(), wr.getUserId());
                return;
            }

            // Lỗi CHẮC CHẮN CHƯA GỬI (connection refused, DNS, 4xx): an toàn để hoàn/retry.
            // Xóa transferRef để write-ahead lần sau ghi ref mới sạch.
            wr.setTransferRef(null);
            transition(wr, WithdrawalStatus.TRANSFER_FAILED, "SYSTEM", "SYSTEM",
                    "TIKLUY chưa nhận được lệnh: " + errorMsg);

            String errorCode = extractErrorCode(errorMsg);
            if (errorCode != null && cfg.isNonRetryable(errorCode)) {
                log.error("withdrawal.nonRetryableError withdrawalId={} errorCode={} amount={} — hoàn tiền về ví",
                        wr.getId(), errorCode, wr.getAmount());
                markFailed(wr, "Lỗi không thể retry: " + errorCode + ". Vui lòng kiểm tra tài khoản chi VNFITE.");
                return;
            }

            if (wr.canRetry()) {
                wr.setRetryCount(wr.getRetryCount() + 1);
                withdrawalRepository.save(wr);
                log.warn("withdrawal.tikluy.notSent.retry withdrawalId={} attempt={}/{}: {}",
                        wr.getId(), wr.getRetryCount(), wr.getMaxRetries(), errorMsg);
                initiateTransfer(wr, "SYSTEM");
            } else {
                markFailed(wr, "TIKLUY chưa nhận lệnh sau " + wr.getMaxRetries() + " lần: " + errorMsg);
            }
        }
    }

    /**
     * Phân biệt lỗi "chắc chắn lệnh chưa rời hệ thống" với lỗi "mơ hồ (có thể đã gửi)".
     * Chỉ trả true khi CHẮC CHẮN TIKLUY chưa nhận được request:
     *   - ConnectException / NoRouteToHost / UnknownHostException → chưa kết nối được.
     *   - HttpClientErrorException (4xx) → TIKLUY từ chối request, chưa thực hiện chuyển.
     * Read-timeout (SocketTimeoutException) và 5xx được coi là MƠ HỒ → trả false (không retry).
     */
    private boolean isDefinitelyNotSent(Throwable e) {
        Throwable t = e;
        while (t != null) {
            if (t instanceof java.net.ConnectException
                    || t instanceof java.net.NoRouteToHostException
                    || t instanceof java.net.UnknownHostException) {
                return true;
            }
            if (t instanceof org.springframework.web.client.HttpClientErrorException) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }

    private void markFailed(WithdrawalRequest wr, String reason) {
        transition(wr, WithdrawalStatus.FAILED, "SYSTEM", "SYSTEM", reason);
        releaseFunds(wr, reason);
        updateWalletTxnStatus(wr, TransactionStatus.FAILED);
        log.warn("withdrawal.failed withdrawalId={} reason={}", wr.getId(), reason);
    }

    private void releaseFunds(WithdrawalRequest wr, String reason) {
        releaseLock(wr.getUserId(), wr.getAmount());
        transition(wr, WithdrawalStatus.FUNDS_RELEASED, "SYSTEM", "SYSTEM",
                "Mở khóa " + wr.getAmount() + " VND. Lý do: " + reason);
        log.info("withdrawal.fundsReleased withdrawalId={} amount={}", wr.getId(), wr.getAmount());
    }

    /**
     * Giảm lockedBalance dưới PESSIMISTIC_WRITE (floor 0). Dùng cho cả 2 trường hợp:
     * tiền rời ví khi success (COMPLETED) và hoàn về ví khi fail (FUNDS_RELEASED).
     * Phải khóa row để không đua với luồng đầu tư/rút khác.
     */
    private void releaseLock(String userId, BigDecimal amount) {
        Wallet wallet = walletRepository.findWithLockByUserIdAndIsDeletedFalse(userId).orElse(null);
        if (wallet == null) return;
        BigDecimal newLocked = wallet.getLockedBalance().subtract(amount);
        wallet.setLockedBalance(newLocked.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : newLocked);
        walletRepository.save(wallet);
    }

    private void updateWalletTxnStatus(WithdrawalRequest wr, TransactionStatus status) {
        if (wr.getWalletTxnId() == null) return;
        walletTransactionRepository.findById(wr.getWalletTxnId()).ifPresent(txn -> {
            txn.setStatus(status);
            if (status == TransactionStatus.SUCCESS) {
                txn.setDescription("Rút tiền thành công. FT=" + wr.getMbFtNumber());
                txn.setBalanceAfter(walletService.computeAvailable(
                        walletRepository.findById(wr.getWalletId()).orElseThrow()));
            } else {
                txn.setDescription("Rút tiền thất bại — tiền đã được hoàn về ví.");
            }
            walletTransactionRepository.save(txn);
        });
    }

    private void transition(WithdrawalRequest wr, WithdrawalStatus next,
                            String actorType, String actor, String note) {
        WithdrawalStatus prev = wr.getStatus();
        if (!prev.canTransitionTo(next)) {
            throw new IllegalStateException("Không thể chuyển từ " + prev + " sang " + next);
        }
        wr.setStatus(next);
        withdrawalRepository.save(wr);
        audit(wr, prev, next, actorType, actor, note);
    }

    private void audit(WithdrawalRequest wr, WithdrawalStatus from, WithdrawalStatus to,
                       String actorType, String actor, String note) {
        auditLogRepository.save(WithdrawalAuditLog.builder()
                .withdrawalId(wr.getId())
                .fromStatus(from != null ? from.name() : null)
                .toStatus(to.name())
                .actorType(actorType)
                .actor(actor)
                .note(note)
                .build());
    }

    private void sendOtp(String withdrawalId, String userId) {
        String otp = appProperties.getOtp().isMock() ? "000000"
                : String.format("%06d", new Random().nextInt(1_000_000));
        String ns = appProperties.getRedis().getNamespace();
        redisTemplate.opsForValue().set(
                ns + ":" + OTP_KEY + withdrawalId,
                otp, OTP_TTL_MINUTES, TimeUnit.MINUTES);
        if (appProperties.getOtp().isMock()) {
            log.info("withdrawal.otp.mock withdrawalId={} userId={} otp={}", withdrawalId, userId, otp);
        } else {
            // TODO: gọi notification-service gửi OTP qua SMS/push
            log.info("withdrawal.otp.sent withdrawalId={} userId={}", withdrawalId, userId);
        }
    }

    private WithdrawalTransferConfig activeConfig() {
        return configRepository.findFirstByActiveTrueAndIsDeletedFalse()
                .orElseThrow(() -> new IllegalStateException(
                        "Chưa có cấu hình chuyển tiền. Liên hệ admin."));
    }
}
