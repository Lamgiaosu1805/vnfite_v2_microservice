package com.p2plending.payment.service;

import com.p2plending.payment.config.AppProperties;
import com.p2plending.payment.domain.entity.Wallet;
import com.p2plending.payment.domain.entity.WalletTransaction;
import com.p2plending.payment.domain.enums.TransactionStatus;
import com.p2plending.payment.domain.enums.TransactionType;
import com.p2plending.payment.domain.repository.WalletRepository;
import com.p2plending.payment.domain.repository.WalletTransactionRepository;
import com.p2plending.payment.dto.response.TransactionResponse;
import com.p2plending.payment.dto.response.WalletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletService {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;
    private final TikluyClient tikluyClient;
    private final AppProperties appProperties;
    private final KafkaTemplate<String, String> kafkaTemplate;

    // ─── Wallet creation ──────────────────────────────────────────────────────

    /**
     * Tạo ví cho user sau khi KYC được duyệt.
     * Gọi TIKLUY để tạo VA nếu không ở mock mode.
     */
    @Transactional
    public WalletResponse createWallet(String userId, String fullName, String cccdNumber) {
        if (walletRepository.existsByUserId(userId)) {
            throw new IllegalStateException("Wallet đã tồn tại cho user " + userId);
        }

        String txnId = UUID.randomUUID().toString();
        String accNo;

        if (appProperties.getPayment().isMock()) {
            accNo = "VNF" + String.format("%010d", Math.abs(userId.hashCode() % 9_999_999_999L));
            log.info("txnId={} [MOCK] Tạo VNF account mock: {}", txnId, accNo);
        } else {
            accNo = tikluyClient.createAccount(txnId, fullName, cccdNumber);
            log.info("txnId={} Đã tạo VNF account từ TIKLUY: {}", txnId, accNo);
        }

        Wallet wallet = walletRepository.save(Wallet.builder()
                .userId(userId)
                .vnfAccountNo(accNo)
                .totalBalance(BigDecimal.ZERO)
                .lockedBalance(BigDecimal.ZERO)
                .build());

        return toResponse(wallet);
    }

    // ─── Migration: link tài khoản VNF cũ ────────────────────────────────────

    /**
     * Link tài khoản VNF đã tồn tại trong TIKLUY vào payment-service.
     * Dùng khi migrate từ VNFITE cũ sang mới — không tạo account mới, chỉ ghi wallet.
     *
     * Idempotent: nếu wallet đã tồn tại thì trả về luôn, không báo lỗi.
     */
    @Transactional
    public WalletResponse linkExistingAccount(String userId, String vnfAccountNo) {
        // Idempotent: ví đã được migrate rồi thì trả về luôn
        return walletRepository.findByUserIdAndIsDeletedFalse(userId)
                .map(this::toResponse)
                .orElseGet(() -> {
                    String txnId = "MIGRATE-" + UUID.randomUUID();
                    BigDecimal totalBalance = BigDecimal.ZERO;
                    BigDecimal lockedBalance = BigDecimal.ZERO;

                    if (!appProperties.getPayment().isMock()) {
                        try {
                            // Lấy số dư thực từ TIKLUY
                            TikluyClient.TikluyAccountInfo info = tikluyClient.getAccount(txnId, vnfAccountNo);
                            totalBalance  = info.getTotalMoney()  != null ? info.getTotalMoney()  : BigDecimal.ZERO;
                            lockedBalance = info.getLockedMoney() != null ? info.getLockedMoney() : BigDecimal.ZERO;
                            log.info("txnId={} Migrate account: userId={} accNo={} balance={}",
                                    txnId, userId, vnfAccountNo, totalBalance);
                        } catch (Exception e) {
                            log.warn("txnId={} Không lấy được balance từ TIKLUY, dùng 0: {}", txnId, e.getMessage());
                        }
                    } else {
                        log.info("txnId={} [MOCK] Migrate account: userId={} accNo={}", txnId, userId, vnfAccountNo);
                    }

                    Wallet wallet = walletRepository.save(Wallet.builder()
                            .userId(userId)
                            .vnfAccountNo(vnfAccountNo)
                            .totalBalance(totalBalance)
                            .lockedBalance(lockedBalance)
                            .build());

                    return toResponse(wallet);
                });
    }

    // ─── Queries ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public WalletResponse getWallet(String userId) {
        Wallet wallet = findByUser(userId);
        return toResponse(wallet);
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> getTransactions(String userId, int page, int size) {
        Wallet wallet = findByUser(userId);
        return transactionRepository.findByWalletIdAndIsDeletedFalseOrderByCreatedAtDesc(
                wallet.getId(), PageRequest.of(page, size))
                .map(this::toTxnResponse);
    }

    // ─── Deposit callback from TIKLUY ────────────────────────────────────────

    /**
     * TIKLUY gọi endpoint này khi MB Bank webhook xác nhận nạp tiền thành công.
     *
     * @param txnId      requestId từ header TIKLUY
     * @param accNo      VNF account number
     * @param amount     Số tiền nạp (VND)
     * @param referenceId referenceNumber từ MB Bank (dùng dedup)
     */
    @Transactional
    public void processDeposit(String txnId, String accNo, BigDecimal amount, String referenceId) {
        // Dedup: tránh xử lý trùng webhook
        if (referenceId != null && transactionRepository.existsByReferenceId(referenceId)) {
            log.warn("txnId={} Duplicate deposit referenceId={}, skip", txnId, referenceId);
            return;
        }

        Wallet wallet = walletRepository.findByVnfAccountNoAndIsDeletedFalse(accNo)
                .orElseThrow(() -> {
                    log.error("txnId={} Deposit callback: không tìm thấy wallet cho accNo={}", txnId, accNo);
                    return new IllegalArgumentException("Wallet không tồn tại: " + accNo);
                });

        wallet.setTotalBalance(wallet.getTotalBalance().add(amount));
        walletRepository.save(wallet);

        WalletTransaction txn = transactionRepository.save(WalletTransaction.builder()
                .walletId(wallet.getId())
                .type(TransactionType.DEPOSIT)
                .amount(amount)
                .status(TransactionStatus.SUCCESS)
                .referenceId(referenceId)
                .description("Nạp tiền vào ví VNFITE")
                .balanceAfter(wallet.getTotalBalance())
                .build());

        log.info("txnId={} Deposit processed: accNo={} amount={} newBalance={}",
                txnId, accNo, amount, wallet.getTotalBalance());

        // Publish Kafka event để các service khác biết user đã có tiền
        publishDepositEvent(wallet.getUserId(), amount, wallet.getTotalBalance(), txn.getId());
    }

    // ─── Balance lock/unlock (dùng khi đầu tư) ────────────────────────────────

    /**
     * Khóa số tiền khi user đặt offer đầu tư.
     */
    @Transactional
    public void lockAmount(String userId, BigDecimal amount, String description) {
        Wallet wallet = findByUser(userId);
        BigDecimal available = wallet.getAvailableBalance();

        if (available.compareTo(amount) < 0) {
            throw new IllegalStateException(
                    "Số dư khả dụng không đủ. Khả dụng: " + available + ", yêu cầu: " + amount);
        }

        wallet.setLockedBalance(wallet.getLockedBalance().add(amount));
        walletRepository.save(wallet);

        transactionRepository.save(WalletTransaction.builder()
                .walletId(wallet.getId())
                .type(TransactionType.INVEST)
                .amount(amount)
                .status(TransactionStatus.SUCCESS)
                .description(description != null ? description : "Khóa tiền đầu tư")
                .balanceAfter(wallet.getAvailableBalance())
                .build());
    }

    /**
     * Mở khóa số tiền khi offer bị từ chối/hủy.
     */
    @Transactional
    public void unlockAmount(String userId, BigDecimal amount, String description) {
        Wallet wallet = findByUser(userId);
        BigDecimal newLocked = wallet.getLockedBalance().subtract(amount);
        if (newLocked.compareTo(BigDecimal.ZERO) < 0) newLocked = BigDecimal.ZERO;

        wallet.setLockedBalance(newLocked);
        walletRepository.save(wallet);

        transactionRepository.save(WalletTransaction.builder()
                .walletId(wallet.getId())
                .type(TransactionType.INVEST_REFUND)
                .amount(amount)
                .status(TransactionStatus.SUCCESS)
                .description(description != null ? description : "Hoàn tiền đầu tư")
                .balanceAfter(wallet.getAvailableBalance())
                .build());
    }

    /**
     * Trừ tiền khi khoản vay được giải ngân (tiền locked → debit thật).
     */
    @Transactional
    public void debitInvestment(String userId, BigDecimal amount, String description) {
        Wallet wallet = findByUser(userId);

        // Bỏ khóa và trừ tổng
        BigDecimal newLocked = wallet.getLockedBalance().subtract(amount);
        if (newLocked.compareTo(BigDecimal.ZERO) < 0) newLocked = BigDecimal.ZERO;
        wallet.setLockedBalance(newLocked);
        wallet.setTotalBalance(wallet.getTotalBalance().subtract(amount));
        walletRepository.save(wallet);

        transactionRepository.save(WalletTransaction.builder()
                .walletId(wallet.getId())
                .type(TransactionType.INVEST)
                .amount(amount)
                .status(TransactionStatus.SUCCESS)
                .description(description != null ? description : "Giải ngân khoản đầu tư")
                .balanceAfter(wallet.getAvailableBalance())
                .build());
    }

    /**
     * Nhận tiền hoàn trả (gốc + lãi) từ khoản cho vay.
     */
    @Transactional
    public void creditRepayment(String userId, BigDecimal amount, String description, String externalRef) {
        Wallet wallet = findByUser(userId);
        wallet.setTotalBalance(wallet.getTotalBalance().add(amount));
        walletRepository.save(wallet);

        transactionRepository.save(WalletTransaction.builder()
                .walletId(wallet.getId())
                .type(TransactionType.REPAYMENT)
                .amount(amount)
                .status(TransactionStatus.SUCCESS)
                .externalRef(externalRef)
                .description(description != null ? description : "Nhận tiền hoàn trả")
                .balanceAfter(wallet.getTotalBalance())
                .build());
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    public Wallet findByUser(String userId) {
        return walletRepository.findByUserIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new IllegalStateException(
                        "Wallet chưa được tạo cho user " + userId + ". KYC cần được duyệt trước."));
    }

    private void publishDepositEvent(String userId, BigDecimal amount, BigDecimal balance, String txnId) {
        try {
            String payload = String.format(
                    "{\"userId\":\"%s\",\"amount\":%s,\"balance\":%s,\"txnId\":\"%s\"}",
                    userId, amount, balance, txnId);
            kafkaTemplate.send("payment.deposit_completed", userId, payload);
        } catch (Exception e) {
            log.error("Failed to publish deposit event for user {}: {}", userId, e.getMessage());
        }
    }

    private WalletResponse toResponse(Wallet w) {
        return WalletResponse.builder()
                .walletId(w.getId())
                .vnfAccountNo(w.getVnfAccountNo())
                .totalBalance(w.getTotalBalance())
                .lockedBalance(w.getLockedBalance())
                .availableBalance(w.getAvailableBalance())
                .createdAt(w.getCreatedAt())
                .build();
    }

    private TransactionResponse toTxnResponse(WalletTransaction t) {
        return TransactionResponse.builder()
                .id(t.getId())
                .type(t.getType())
                .amount(t.getAmount())
                .status(t.getStatus())
                .description(t.getDescription())
                .balanceAfter(t.getBalanceAfter())
                .createdAt(t.getCreatedAt())
                .build();
    }
}
