package com.p2plending.payment.service;

import com.p2plending.payment.config.AppProperties;
import com.p2plending.payment.domain.entity.*;
import com.p2plending.payment.domain.enums.TransactionStatus;
import com.p2plending.payment.domain.enums.TransactionType;
import com.p2plending.payment.domain.enums.WithdrawalStatus;
import com.p2plending.payment.domain.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
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
    private final StringRedisTemplate redisTemplate;
    private final AppProperties appProperties;

    private static final String OTP_KEY = "withdrawal_otp:";
    private static final long OTP_TTL_MINUTES = 5;

    public record TransferAttempt(
            String withdrawalId,
            String transferRef,
            String vnfAccountNo,
            String bankCode,
            String bankAccountNo,
            BigDecimal amount) {
    }

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

    // ─── Bước 2 / TX1: xác nhận OTP, lock tiền, ghi write-ahead ─────────────

    @Transactional
    public TransferAttempt prepareConfirmedTransfer(String userId, String withdrawalId, String otp) {
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

        return prepareTransferAttempt(wr, "SYSTEM", "SYSTEM", "Bắt đầu chuyển tiền qua TIKLUY");
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
    public Optional<TransferAttempt> processTransferCallback(String transferRef, boolean success,
                                                             String ftNumber, String errorCode) {
        WithdrawalRequest wr = withdrawalRepository
                .findWithLockByTransferRefAndIsDeletedFalse(transferRef)
                .orElse(null);

        if (wr == null) {
            log.warn("withdrawal.callback.notFound transferRef={}", transferRef);
            return Optional.empty();
        }

        if (wr.getStatus() != WithdrawalStatus.PROCESSING
                && wr.getStatus() != WithdrawalStatus.TRANSFER_INITIATED) {
            log.warn("withdrawal.callback.skipped withdrawalId={} status={}", wr.getId(), wr.getStatus());
            return Optional.empty();
        }

        if (success) {
            if (wr.getStatus() == WithdrawalStatus.TRANSFER_INITIATED) {
                transition(wr, WithdrawalStatus.PROCESSING, "SYSTEM", "TIKLUY",
                        "Callback thành công đến trước khi ghi nhận phản hồi khởi tạo");
            }
            wr.setMbFtNumber(ftNumber);
            transition(wr, WithdrawalStatus.COMPLETED, "SYSTEM", "TIKLUY",
                    "Chuyển tiền thành công. FT=" + ftNumber);
            // TIKLUY/MB đã trừ tiền thật khỏi VA → giải phóng phần đã khóa (tiền đã rời ví).
            // Nếu KHÔNG bỏ lock ở đây, lockedBalance phình vĩnh viễn → tiền nạp sau bị "vô hình".
            releaseLock(wr.getUserId(), wr.getAmount());
            updateWalletTxnStatus(wr, TransactionStatus.SUCCESS);
            log.info("withdrawal.completed withdrawalId={} transferRef={} FT={}", wr.getId(), transferRef, ftNumber);
            return Optional.empty();
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
                return Optional.empty();
            }

            if (wr.canRetry()) {
                wr.setRetryCount(wr.getRetryCount() + 1);
                wr.setTransferRef(null);
                withdrawalRepository.save(wr);
                log.info("withdrawal.retry withdrawalId={} attempt={}/{}", wr.getId(), wr.getRetryCount(), wr.getMaxRetries());
                return Optional.of(prepareTransferAttempt(
                        wr, "SYSTEM", "SYSTEM", "Retry sau callback thất bại"));
            } else {
                log.error("[MB-ERROR] MAX-RETRY-EXCEEDED withdrawalId={} lastErrorCode='{}' amount={} — hoàn tiền về ví",
                        wr.getId(), errorCode, wr.getAmount());
                markFailed(wr, "Transfer thất bại sau " + wr.getMaxRetries() + " lần thử. errorCode=" + errorCode);
                return Optional.empty();
            }
        }
    }

    // ─── Ops: retry thủ công (sau khi stuck hoặc TRANSFER_FAILED) ────────────

    @Transactional
    public TransferAttempt prepareManualRetry(String adminId, String withdrawalId) {
        WithdrawalRequest wr = withdrawalRepository
                .findWithLockByIdAndIsDeletedFalse(withdrawalId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy yêu cầu rút tiền."));

        if (wr.getStatus() != WithdrawalStatus.TRANSFER_FAILED) {
            throw new IllegalStateException(
                    "Chỉ có thể retry khi trạng thái là TRANSFER_FAILED.");
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
        log.info("withdrawal.manualRetry withdrawalId={} adminId={} attempt={}/{} newTxnId={}",
                withdrawalId, adminId, wr.getRetryCount(), wr.getMaxRetries(),
                wr.getId() + "-R" + wr.getRetryCount());
        return prepareTransferAttempt(
                wr, "ADMIN", adminId, "Ops retry thủ công lần " + wr.getRetryCount());
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
                .findWithLockByIdAndIsDeletedFalse(withdrawalId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy yêu cầu rút tiền."));

        if (wr.getStatus() != WithdrawalStatus.PROCESSING
                && wr.getStatus() != WithdrawalStatus.TRANSFER_INITIATED) {
            throw new IllegalStateException(
                    "Chỉ resolve được giao dịch đang ở PROCESSING/TRANSFER_INITIATED. Hiện tại: " + wr.getStatus());
        }

        if (wasSent) {
            if (wr.getStatus() == WithdrawalStatus.TRANSFER_INITIATED) {
                transition(wr, WithdrawalStatus.PROCESSING, "ADMIN", adminId,
                        "Ops xác minh kết quả trước khi ghi nhận phản hồi khởi tạo");
            }
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

    // ─── Reconciliation DB steps — scheduler nằm ở orchestrator ──────────────

    @Transactional(readOnly = true)
    public List<String> findStuckWithdrawalIds(LocalDateTime threshold) {
        return withdrawalRepository.findStuckWithdrawals(List.of(
                        WithdrawalStatus.TRANSFER_INITIATED,
                        WithdrawalStatus.PROCESSING), threshold).stream()
                .map(WithdrawalRequest::getId)
                .toList();
    }

    @Transactional
    public Optional<TransferAttempt> prepareStuckRetry(String withdrawalId, LocalDateTime threshold) {
        WithdrawalRequest wr = withdrawalRepository
                .findWithLockByIdAndIsDeletedFalse(withdrawalId)
                .orElse(null);
        if (wr == null
                || (wr.getStatus() != WithdrawalStatus.TRANSFER_INITIATED
                    && wr.getStatus() != WithdrawalStatus.PROCESSING)
                || wr.getUpdatedAt() == null
                || !wr.getUpdatedAt().isBefore(threshold)) {
            return Optional.empty();
        }

        log.warn("withdrawal.stuck withdrawalId={} status={} transferRef={} amount={} updatedAt={}",
                wr.getId(), wr.getStatus(), wr.getTransferRef(), wr.getAmount(), wr.getUpdatedAt());

        // NẾU transferRef đã được ghi (write-ahead) → TIKLUY đã nhận lệnh.
        // Không được retry vì có thể TIKLUY đang xử lý hoặc callback chưa về.
        // Chỉ alert ops để kiểm tra trạng thái bên TIKLUY/MB rồi quyết định.
        if (wr.getTransferRef() != null) {
            log.error("[RECONCILE-STUCK] withdrawalId={} transferRef={} amount={} — TIKLUY đã nhận lệnh, chưa có callback. " +
                      "KHÔNG tự retry. Ops cần kiểm tra bên TIKLUY/MB rồi dùng /retry nếu chắc chắn chưa chuyển.",
                    wr.getId(), wr.getTransferRef(), wr.getAmount());
            return Optional.empty();
        }

        // transferRef = null → lệnh chưa gửi được sang TIKLUY → an toàn để retry
        transition(wr, WithdrawalStatus.TRANSFER_FAILED, "SYSTEM", "RECONCILER",
                "Kẹt quá ngưỡng đối soát, chưa gửi được lệnh sang TIKLUY");

        if (wr.canRetry()) {
            wr.setRetryCount(wr.getRetryCount() + 1);
            withdrawalRepository.save(wr);
            log.info("withdrawal.reconcile.retry withdrawalId={} attempt={}/{}", wr.getId(), wr.getRetryCount(), wr.getMaxRetries());
            return Optional.of(prepareTransferAttempt(
                    wr, "SYSTEM", "RECONCILER", "Reconcile retry khi chưa gửi được lệnh"));
        } else {
            markFailed(wr, "Giao dịch kẹt, chưa gửi sang TIKLUY và đã hết lần retry");
            return Optional.empty();
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

    private TransferAttempt prepareTransferAttempt(WithdrawalRequest wr, String actorType,
                                                   String actor, String note) {
        transition(wr, WithdrawalStatus.TRANSFER_INITIATED, actorType, actor, note);

        Wallet wallet = walletRepository.findById(wr.getWalletId())
                .orElseThrow(() -> new IllegalStateException("Wallet không tồn tại."));
        LinkedBank bank = linkedBankRepository.findByIdAndIsDeletedFalse(wr.getLinkedBankId())
                .orElseThrow(() -> new IllegalStateException("Tài khoản ngân hàng không còn hợp lệ."));

        // Idempotency: txnId deterministic theo withdrawalId + retryCount.
        // Cùng một lần thử luôn dùng cùng txnId → TIKLUY dedup nếu nhận trùng.
        String txnId = wr.getId() + "-R" + wr.getRetryCount();

        // Write-ahead: lưu transferRef vào DB TRƯỚC khi gọi TIKLUY.
        // Nếu sau đó bị timeout, ta biết TIKLUY đã nhận lệnh → không retry mù.
        wr.setTransferRef(txnId);
        withdrawalRepository.save(wr);

        return new TransferAttempt(
                wr.getId(), txnId, wallet.getVnfAccountNo(), bank.getBankCode(),
                bank.getBankAccountNo(), wr.getAmount());
    }

    @Transactional
    public void recordTransferAccepted(String withdrawalId, String expectedTransferRef,
                                       String providerReference) {
        WithdrawalRequest wr = withdrawalRepository
                .findWithLockByIdAndIsDeletedFalse(withdrawalId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy yêu cầu rút tiền."));

        if (wr.getStatus() != WithdrawalStatus.TRANSFER_INITIATED
                || !expectedTransferRef.equals(wr.getTransferRef())) {
            log.warn("withdrawal.dispatch.accepted.skipped withdrawalId={} expectedRef={} actualRef={} status={}",
                    withdrawalId, expectedTransferRef, wr.getTransferRef(), wr.getStatus());
            return;
        }

        wr.setFailureReason(null);
        transition(wr, WithdrawalStatus.PROCESSING, "SYSTEM", "SYSTEM",
                "TIKLUY nhận lệnh. providerRef=" + providerReference);
        log.info("withdrawal.tikluy.sent withdrawalId={} txnId={} providerRef={} amount={}",
                wr.getId(), expectedTransferRef, providerReference, wr.getAmount());
    }

    @Transactional
    public Optional<TransferAttempt> recordTransferDispatchFailure(
            String withdrawalId, String expectedTransferRef, String errorMessage,
            boolean definitelyNotSent, String errorCode) {
        WithdrawalRequest wr = withdrawalRepository
                .findWithLockByIdAndIsDeletedFalse(withdrawalId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy yêu cầu rút tiền."));

        if (wr.getStatus() != WithdrawalStatus.TRANSFER_INITIATED
                || !expectedTransferRef.equals(wr.getTransferRef())) {
            log.warn("withdrawal.dispatch.failure.skipped withdrawalId={} expectedRef={} actualRef={} status={}",
                    withdrawalId, expectedTransferRef, wr.getTransferRef(), wr.getStatus());
            return Optional.empty();
        }

        wr.setFailureReason(errorMessage);
        if (!definitelyNotSent) {
            transition(wr, WithdrawalStatus.PROCESSING, "SYSTEM", "SYSTEM",
                    "TIKLUY timeout/không rõ kết quả — chờ callback/đối soát, KHÔNG tự retry: "
                            + errorMessage);
            log.error("[TRANSFER-AMBIGUOUS] withdrawalId={} transferRef={} amount={} userId={} — "
                            + "có thể đã gửi sang MB. Giữ khóa tiền, chờ callback hoặc ops đối soát thủ công.",
                    wr.getId(), wr.getTransferRef(), wr.getAmount(), wr.getUserId());
            return Optional.empty();
        }

        wr.setTransferRef(null);
        transition(wr, WithdrawalStatus.TRANSFER_FAILED, "SYSTEM", "SYSTEM",
                "TIKLUY chưa nhận được lệnh: " + errorMessage);

        WithdrawalTransferConfig cfg = activeConfig();
        if (errorCode != null && cfg.isNonRetryable(errorCode)) {
            log.error("withdrawal.nonRetryableError withdrawalId={} errorCode={} amount={} — hoàn tiền về ví",
                    wr.getId(), errorCode, wr.getAmount());
            markFailed(wr, "Lỗi không thể retry: " + errorCode + ". Vui lòng kiểm tra tài khoản chi VNFITE.");
            return Optional.empty();
        }

        if (wr.canRetry()) {
            wr.setRetryCount(wr.getRetryCount() + 1);
            withdrawalRepository.save(wr);
            log.warn("withdrawal.tikluy.notSent.retry withdrawalId={} attempt={}/{}: {}",
                    wr.getId(), wr.getRetryCount(), wr.getMaxRetries(), errorMessage);
            return Optional.of(prepareTransferAttempt(
                    wr, "SYSTEM", "SYSTEM", "Retry sau lỗi chắc chắn chưa gửi"));
        }

        markFailed(wr, "TIKLUY chưa nhận lệnh sau " + wr.getMaxRetries() + " lần: " + errorMessage);
        return Optional.empty();
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
