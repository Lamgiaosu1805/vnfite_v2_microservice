package com.p2plending.payment.service;

import com.p2plending.payment.config.AppProperties;
import com.p2plending.payment.domain.entity.LinkedBank;
import com.p2plending.payment.domain.entity.Wallet;
import com.p2plending.payment.domain.entity.WalletTransaction;
import com.p2plending.payment.domain.enums.TransactionStatus;
import com.p2plending.payment.domain.enums.TransactionType;
import com.p2plending.payment.domain.repository.LinkedBankRepository;
import com.p2plending.payment.domain.repository.WalletRepository;
import com.p2plending.payment.domain.repository.WalletTransactionRepository;
import com.p2plending.payment.dto.request.WithdrawRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Luồng rút tiền (2 bước, có OTP):
 *
 * Bước 1 — POST /api/payment/wallet/withdraw
 *   → Kiểm tra số dư khả dụng
 *   → Lưu pending data vào Redis (TTL 5 phút)
 *   → Gửi OTP
 *
 * Bước 2 — POST /api/payment/wallet/withdraw/confirm
 *   → Xác thực OTP
 *   → Khóa số dư (lockedBalance += amount)
 *   → Gọi TIKLUY fund transfer
 *   → Trừ số dư thực (totalBalance -= amount, lockedBalance -= amount)
 *   → Ghi WalletTransaction
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WithdrawService {

    private final WalletService walletService;
    private final WalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;
    private final LinkedBankRepository linkedBankRepository;
    private final TikluyClient tikluyClient;
    private final StringRedisTemplate redisTemplate;
    private final AppProperties appProperties;
    private final KafkaTemplate<String, String> kafkaTemplate;

    private static final String OTP_KEY_PREFIX = "withdraw_otp:";
    private static final String PENDING_KEY_PREFIX = "withdraw_pending:";
    private static final long OTP_TTL_MINUTES = 5;

    // ─── Bước 1: Khởi tạo rút tiền, gửi OTP ─────────────────────────────────

    @Transactional(readOnly = true)
    public void initiateWithdraw(String userId, WithdrawRequest req) {
        Wallet wallet = walletService.findByUser(userId);
        BigDecimal available = wallet.getAvailableBalance();

        if (available.compareTo(req.getAmount()) < 0) {
            throw new IllegalStateException(
                    "Số dư khả dụng không đủ. Khả dụng: " + available + " VND, yêu cầu: " + req.getAmount() + " VND");
        }

        LinkedBank bank = linkedBankRepository
                .findByIdAndUserIdAndIsDeletedFalse(req.getLinkedBankId(), userId)
                .orElseThrow(() -> new IllegalArgumentException("Tài khoản ngân hàng không tồn tại"));

        // Lưu pending data vào Redis
        String pendingValue = req.getAmount() + "|" + bank.getId();
        String ns = appProperties.getRedis().getNamespace();
        redisTemplate.opsForValue().set(
                ns + ":" + PENDING_KEY_PREFIX + userId,
                pendingValue,
                OTP_TTL_MINUTES, TimeUnit.MINUTES);

        // Tạo và lưu OTP
        String otp = generateOtp();
        redisTemplate.opsForValue().set(
                ns + ":" + OTP_KEY_PREFIX + userId,
                otp,
                OTP_TTL_MINUTES, TimeUnit.MINUTES);

        if (appProperties.getOtp().isMock()) {
            log.info("userId={} [MOCK] OTP rút tiền: {}", userId, otp);
        } else {
            // TODO: gọi notification-service gửi OTP qua SMS/push
            log.info("userId={} OTP rút tiền đã gửi", userId);
        }
    }

    // ─── Bước 2: Xác nhận OTP, thực hiện rút tiền ────────────────────────────

    @Transactional
    public void confirmWithdraw(String userId, String otp) {
        String ns = appProperties.getRedis().getNamespace();
        String otpKey     = ns + ":" + OTP_KEY_PREFIX     + userId;
        String pendingKey = ns + ":" + PENDING_KEY_PREFIX + userId;

        // Xác thực OTP
        String storedOtp = redisTemplate.opsForValue().get(otpKey);
        if (storedOtp == null) {
            throw new IllegalStateException("OTP đã hết hạn. Vui lòng yêu cầu lại.");
        }
        if (!storedOtp.equals(otp)) {
            throw new IllegalStateException("OTP không chính xác");
        }

        // Lấy pending data
        String pendingValue = redisTemplate.opsForValue().get(pendingKey);
        if (pendingValue == null) {
            throw new IllegalStateException("Phiên rút tiền đã hết hạn. Vui lòng thực hiện lại.");
        }

        String[] parts = pendingValue.split("\\|");
        BigDecimal amount    = new BigDecimal(parts[0]);
        String    bankId     = parts[1];

        // Xóa OTP và pending data khỏi Redis (one-time use)
        redisTemplate.delete(otpKey);
        redisTemplate.delete(pendingKey);

        Wallet wallet = walletService.findByUser(userId);
        LinkedBank bank = linkedBankRepository
                .findByIdAndUserIdAndIsDeletedFalse(bankId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Tài khoản ngân hàng không còn hợp lệ"));

        // Kiểm tra lại số dư (có thể đã thay đổi trong lúc chờ OTP)
        if (wallet.getAvailableBalance().compareTo(amount) < 0) {
            throw new IllegalStateException("Số dư khả dụng không đủ để thực hiện rút tiền");
        }

        // Khóa số dư ngay lập tức
        wallet.setLockedBalance(wallet.getLockedBalance().add(amount));
        walletRepository.save(wallet);

        String txnId = UUID.randomUUID().toString();
        String externalRef;

        if (appProperties.getPayment().isMock()) {
            externalRef = "MOCK_WITHDRAW_" + txnId;
            log.info("userId={} [MOCK] Rút tiền: amount={} bank={} txnId={}",
                    userId, amount, bank.getBankAccountNo(), txnId);
            // Mock: giả lập thành công ngay
            finalizeWithdraw(wallet, amount, externalRef, TransactionStatus.SUCCESS);
        } else {
            try {
                externalRef = tikluyClient.fundTransfer(
                        txnId,
                        wallet.getVnfAccountNo(),
                        bank.getBankCode(),
                        bank.getBankAccountNo(),
                        amount.toPlainString());

                log.info("userId={} Fund transfer PROCESSING: accNo={} amount={} externalRef={}",
                        userId, wallet.getVnfAccountNo(), amount, externalRef);

                // Ghi transaction PENDING — sẽ được cập nhật khi TIKLUY callback
                transactionRepository.save(WalletTransaction.builder()
                        .walletId(wallet.getId())
                        .type(TransactionType.WITHDRAW)
                        .amount(amount)
                        .status(TransactionStatus.PENDING)
                        .externalRef(externalRef)
                        .description("Rút tiền ra " + bank.getBankName() + " " + bank.getBankAccountNo())
                        .build());

            } catch (Exception e) {
                // Fund transfer thất bại: mở khóa số dư
                wallet.setLockedBalance(wallet.getLockedBalance().subtract(amount));
                walletRepository.save(wallet);
                log.error("userId={} Fund transfer thất bại, đã mở khóa số dư: {}", userId, e.getMessage());
                throw new RuntimeException("Rút tiền thất bại: " + e.getMessage(), e);
            }
        }
    }

    /**
     * TIKLUY callback khi withdrawal hoàn tất (SUCCESS hoặc FAILED).
     * Map với endpoint: POST /internal/payment/withdraw-callback
     */
    @Transactional
    public void handleWithdrawCallback(String tikluyTxnId, boolean success) {
        WalletTransaction txn = transactionRepository
                .findByExternalRefAndStatus(tikluyTxnId, TransactionStatus.PENDING)
                .orElse(null);

        if (txn == null) {
            log.warn("Withdraw callback: không tìm thấy PENDING txn externalRef={}", tikluyTxnId);
            return;
        }

        Wallet wallet = walletRepository.findById(txn.getWalletId())
                .orElseThrow(() -> new IllegalStateException("Wallet không tồn tại: " + txn.getWalletId()));

        // Bỏ locked
        BigDecimal lockedNew = wallet.getLockedBalance().subtract(txn.getAmount());
        wallet.setLockedBalance(lockedNew.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : lockedNew);

        if (success) {
            wallet.setTotalBalance(wallet.getTotalBalance().subtract(txn.getAmount()));
            txn.setStatus(TransactionStatus.SUCCESS);
            txn.setBalanceAfter(wallet.getAvailableBalance());
            publishWithdrawEvent(wallet.getUserId(), txn.getAmount(), wallet.getTotalBalance(), txn.getId());
        } else {
            // Thất bại: tiền trở về, chỉ bỏ locked
            txn.setStatus(TransactionStatus.FAILED);
            txn.setDescription(txn.getDescription() + " [THẤT BẠI - tiền đã hoàn về ví]");
            txn.setBalanceAfter(wallet.getAvailableBalance());
        }

        walletRepository.save(wallet);
        transactionRepository.save(txn);
        log.info("Withdraw callback: txnId={} status={}", tikluyTxnId, txn.getStatus());
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void finalizeWithdraw(Wallet wallet, BigDecimal amount, String externalRef, TransactionStatus status) {
        wallet.setLockedBalance(wallet.getLockedBalance().subtract(amount));
        wallet.setTotalBalance(wallet.getTotalBalance().subtract(amount));
        walletRepository.save(wallet);

        transactionRepository.save(WalletTransaction.builder()
                .walletId(wallet.getId())
                .type(TransactionType.WITHDRAW)
                .amount(amount)
                .status(status)
                .externalRef(externalRef)
                .description("Rút tiền ra ngân hàng")
                .balanceAfter(wallet.getAvailableBalance())
                .build());
    }

    private String generateOtp() {
        if (appProperties.getOtp().isMock()) return "000000";
        return String.format("%06d", new Random().nextInt(1_000_000));
    }

    private void publishWithdrawEvent(String userId, BigDecimal amount, BigDecimal balance, String txnId) {
        try {
            String payload = String.format(
                    "{\"userId\":\"%s\",\"amount\":%s,\"balance\":%s,\"txnId\":\"%s\"}",
                    userId, amount, balance, txnId);
            kafkaTemplate.send("payment.withdraw_completed", userId, payload);
        } catch (Exception e) {
            log.error("Failed to publish withdraw event for user {}: {}", userId, e.getMessage());
        }
    }
}
