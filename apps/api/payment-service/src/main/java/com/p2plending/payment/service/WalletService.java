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
                .lockedBalance(BigDecimal.ZERO)
                .build());

        return toResponse(wallet);
    }

    // ─── Migration: link tài khoản VNF cũ ────────────────────────────────────

    @Transactional
    public WalletResponse linkExistingAccount(String userId, String vnfAccountNo) {
        return walletRepository.findByUserIdAndIsDeletedFalse(userId)
                .map(this::toResponse)
                .orElseGet(() -> {
                    String txnId = "MIGRATE-" + UUID.randomUUID();
                    BigDecimal lockedBalance = BigDecimal.ZERO;

                    if (!appProperties.getPayment().isMock()) {
                        try {
                            TikluyClient.TikluyAccountInfo info = tikluyClient.getAccount(txnId, vnfAccountNo);
                            lockedBalance = info.getLockedMoney() != null ? info.getLockedMoney() : BigDecimal.ZERO;
                            log.info("txnId={} Migrate account: userId={} accNo={} locked={}",
                                    txnId, userId, vnfAccountNo, lockedBalance);
                        } catch (Exception e) {
                            log.warn("txnId={} Không lấy được locked balance từ TIKLUY, dùng 0: {}", txnId, e.getMessage());
                        }
                    } else {
                        log.info("txnId={} [MOCK] Migrate account: userId={} accNo={}", txnId, userId, vnfAccountNo);
                    }

                    Wallet wallet = walletRepository.save(Wallet.builder()
                            .userId(userId)
                            .vnfAccountNo(vnfAccountNo)
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
     * Không cập nhật local balance — số dư thực luôn lấy từ TIKLUY.
     *
     * @param runningBalance Số dư tài khoản TIKLUY sau giao dịch (từ callback, nullable)
     */
    @Transactional
    public void processDeposit(String txnId, String accNo, BigDecimal amount, String referenceId,
                               BigDecimal runningBalance) {
        if (referenceId != null && transactionRepository.existsByReferenceId(referenceId)) {
            log.warn("txnId={} Duplicate deposit referenceId={}, skip", txnId, referenceId);
            return;
        }

        Wallet wallet = walletRepository.findByVnfAccountNoAndIsDeletedFalse(accNo)
                .orElseThrow(() -> {
                    log.error("txnId={} Deposit callback: không tìm thấy wallet cho accNo={}", txnId, accNo);
                    return new IllegalArgumentException("Wallet không tồn tại: " + accNo);
                });

        // balanceAfter trong lịch sử ví luôn là số dư khả dụng sau giao dịch,
        // không phải tổng số dư TIKLUY, để nhất quán với các biến động lock/unlock/rút tiền.
        BigDecimal totalAfter = (runningBalance != null) ? runningBalance : getTikluyBalance(wallet);
        BigDecimal availableAfter = computeAvailableFromTotal(totalAfter, wallet);

        WalletTransaction txn = transactionRepository.save(WalletTransaction.builder()
                .walletId(wallet.getId())
                .type(TransactionType.DEPOSIT)
                .amount(amount)
                .status(TransactionStatus.SUCCESS)
                .referenceId(referenceId)
                .description("Nạp tiền vào ví VNFITE")
                .balanceAfter(availableAfter)
                .build());

        log.info("txnId={} Deposit processed: accNo={} amount={} totalAfter={} availableAfter={}",
                txnId, accNo, amount, totalAfter, availableAfter);

        publishDepositEvent(wallet.getUserId(), amount, availableAfter, txn.getId());
    }

    // ─── Balance lock/unlock (dùng khi đầu tư) ────────────────────────────────

    @Transactional
    public void lockAmount(String userId, BigDecimal amount, String description) {
        Wallet wallet = findByUser(userId);
        BigDecimal available = computeAvailable(wallet);

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
                .balanceAfter(computeAvailable(wallet))
                .build());
    }

    /**
     * Mở khóa tiền khi lệnh đầu tư bị hủy/hết hạn (locked → available trở lại).
     * Không đụng TIKLUY total (lock chỉ tăng lockedBalance, unlock giảm lại).
     * Idempotent theo referenceId (vd "REFUND-{offerId}") để không hoàn trùng khi retry.
     */
    @Transactional
    public void unlockAmount(String userId, BigDecimal amount, String description) {
        unlockAmount(userId, amount, description, null);
    }

    @Transactional
    public void unlockAmount(String userId, BigDecimal amount, String description, String referenceId) {
        if (referenceId != null && transactionRepository.existsByReferenceId(referenceId)) {
            log.warn("Idempotent unlock skip: referenceId={} đã xử lý", referenceId);
            return;
        }

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
                .referenceId(referenceId)
                .description(description != null ? description : "Hoàn tiền đầu tư")
                .balanceAfter(computeAvailable(wallet))
                .build());
    }

    /**
     * Trừ tiền khi khoản vay được giải ngân: tiền rời ví nhà đầu tư (locked → ra khỏi ví).
     * Giảm cả TOTAL_MONEY trên TIKLUY lẫn lockedBalance để số dư khả dụng giảm vĩnh viễn.
     *
     * <p>Idempotent theo {@code referenceId} (vd "DISBURSE-{offerId}"): nếu đã xử lý thì bỏ qua,
     * tránh debit trùng khi loan-service retry giải ngân.
     */
    @Transactional
    public void debitInvestment(String userId, BigDecimal amount, String description) {
        debitInvestment(userId, amount, description, null);
    }

    @Transactional
    public void debitInvestment(String userId, BigDecimal amount, String description, String referenceId) {
        if (referenceId != null && transactionRepository.existsByReferenceId(referenceId)) {
            log.warn("Idempotent debit skip: referenceId={} đã xử lý", referenceId);
            return;
        }

        Wallet wallet = findByUser(userId);
        boolean mock = appProperties.getPayment().isMock();
        BigDecimal tikluyTotal = mock ? BigDecimal.ZERO : getTikluyBalance(wallet);

        BigDecimal newLocked = wallet.getLockedBalance().subtract(amount);
        if (newLocked.compareTo(BigDecimal.ZERO) < 0) newLocked = BigDecimal.ZERO;
        wallet.setLockedBalance(newLocked);
        walletRepository.save(wallet);

        BigDecimal balanceAfter = tikluyTotal.subtract(amount).subtract(newLocked).max(BigDecimal.ZERO);
        transactionRepository.save(WalletTransaction.builder()
                .walletId(wallet.getId())
                .type(TransactionType.INVEST)
                .amount(amount)
                .status(TransactionStatus.SUCCESS)
                .referenceId(referenceId)
                .description(description != null ? description : "Giải ngân khoản đầu tư")
                .balanceAfter(balanceAfter)
                .build());

        // Trừ TIKLUY CUỐI CÙNG: không còn thao tác DB nào sau lời gọi ngoài này, nên nếu DB
        // có lỗi sẽ xảy ra trước (rollback an toàn, chưa trừ TIKLUY). TIKLUY lỗi → ném →
        // toàn bộ rollback. Cửa sổ trùng chỉ còn ở bước commit cuối (cực hiếm).
        if (!mock) {
            String tikluyTxnId = referenceId != null ? referenceId : "DEBIT-" + UUID.randomUUID();
            tikluyClient.deductAccount(tikluyTxnId, wallet.getVnfAccountNo(), amount);
        }
    }

    /**
     * Nhận tiền hoàn trả (gốc + lãi) từ khoản cho vay.
     * TIKLUY sẽ nhận tiền thực từ người gọi vốn trả về — không cập nhật local balance.
     */
    @Transactional
    public void creditRepayment(String userId, BigDecimal amount, String description, String externalRef) {
        Wallet wallet = findByUser(userId);

        transactionRepository.save(WalletTransaction.builder()
                .walletId(wallet.getId())
                .type(TransactionType.REPAYMENT)
                .amount(amount)
                .status(TransactionStatus.SUCCESS)
                .externalRef(externalRef)
                .description(description != null ? description : "Nhận tiền hoàn trả")
                .balanceAfter(computeAvailable(wallet))
                .build());
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    public Wallet findByUser(String userId) {
        return walletRepository.findByUserIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new IllegalStateException(
                        "Wallet chưa được tạo cho user " + userId + ". KYC cần được duyệt trước."));
    }

    /** Số dư thực từ TIKLUY (MB Bank). Mock mode trả 0. */
    public BigDecimal getTikluyBalance(Wallet wallet) {
        if (appProperties.getPayment().isMock()) {
            return BigDecimal.ZERO;
        }
        try {
            return tikluyClient.getAccount("BAL-" + wallet.getId().substring(0, 8), wallet.getVnfAccountNo())
                    .getTotalMoney();
        } catch (Exception e) {
            log.warn("Không lấy được balance từ TIKLUY accNo={}: {}", wallet.getVnfAccountNo(), e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    /** Số dư khả dụng = TIKLUY.totalMoney - local.lockedBalance (≥ 0). */
    public BigDecimal computeAvailable(Wallet wallet) {
        return computeAvailableFromTotal(getTikluyBalance(wallet), wallet);
    }

    private BigDecimal computeAvailableFromTotal(BigDecimal totalBalance, Wallet wallet) {
        BigDecimal total = totalBalance != null ? totalBalance : BigDecimal.ZERO;
        BigDecimal locked = wallet.getLockedBalance() != null ? wallet.getLockedBalance() : BigDecimal.ZERO;
        return total.subtract(locked).max(BigDecimal.ZERO);
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
        BigDecimal total     = getTikluyBalance(w);
        BigDecimal available = total.subtract(w.getLockedBalance()).max(BigDecimal.ZERO);
        return WalletResponse.builder()
                .walletId(w.getId())
                .vnfAccountNo(w.getVnfAccountNo())
                .totalBalance(total)
                .lockedBalance(w.getLockedBalance())
                .availableBalance(available)
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
