package com.p2plending.payment.service;

import com.p2plending.payment.config.AppProperties;
import com.p2plending.payment.domain.entity.Wallet;
import com.p2plending.payment.domain.entity.WalletTransaction;
import com.p2plending.payment.domain.enums.TransactionStatus;
import com.p2plending.payment.domain.enums.TransactionType;
import com.p2plending.payment.domain.repository.WalletRepository;
import com.p2plending.payment.domain.repository.WalletTransactionRepository;
import com.p2plending.payment.dto.response.TransactionResponse;
import com.p2plending.payment.dto.response.SystemTransactionResponse;
import com.p2plending.payment.dto.response.WalletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.time.LocalDateTime;

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
        List<WalletTransaction> transactions =
                transactionRepository.findByWalletIdAndIsDeletedFalseOrderByCreatedAtAsc(wallet.getId());

        List<TransactionResponse> ledger = new ArrayList<>(transactions.size());
        BigDecimal runningAvailable = BigDecimal.ZERO;
        for (WalletTransaction transaction : transactions) {
            runningAvailable = runningAvailable
                    .add(signedAvailableDelta(transaction))
                    .max(BigDecimal.ZERO);
            ledger.add(toTxnResponse(transaction, runningAvailable));
        }

        Collections.reverse(ledger);
        PageRequest pageable = PageRequest.of(page, size);
        int from = Math.min((int) pageable.getOffset(), ledger.size());
        int to = Math.min(from + pageable.getPageSize(), ledger.size());
        return new PageImpl<>(ledger.subList(from, to), pageable, ledger.size());
    }

    @Transactional(readOnly = true)
    public Page<SystemTransactionResponse> getSystemMoneyTransactions(
            TransactionType type,
            TransactionStatus status,
            LocalDateTime fromTime,
            LocalDateTime toTime,
            String search,
            int page,
            int size) {
        String normalizedSearch = search == null || search.isBlank() ? null : search.trim();
        PageRequest pageable = PageRequest.of(page, size);
        // Dùng IN list thay vì (:param IS NULL OR ...) để tránh bug Hibernate 6 với nullable enum parameter
        List<TransactionType> types = type != null
                ? List.of(type)
                : List.of(TransactionType.DEPOSIT, TransactionType.WITHDRAW);
        List<TransactionStatus> statuses = status != null
                ? List.of(status)
                : List.of(TransactionStatus.PENDING, TransactionStatus.SUCCESS, TransactionStatus.FAILED);
        Page<WalletTransaction> transactions = transactionRepository.findSystemMoneyTransactions(
                types,
                statuses,
                fromTime,
                toTime,
                normalizedSearch,
                pageable);

        Map<String, Wallet> wallets = walletRepository.findAllById(
                        transactions.getContent().stream()
                                .map(WalletTransaction::getWalletId)
                                .distinct()
                                .toList())
                .stream()
                .filter(wallet -> !wallet.isDeleted())
                .collect(Collectors.toMap(Wallet::getId, Function.identity()));

        return transactions.map(transaction -> {
            Wallet wallet = wallets.get(transaction.getWalletId());
            return SystemTransactionResponse.builder()
                    .id(transaction.getId())
                    .userId(wallet != null ? wallet.getUserId() : null)
                    .walletId(transaction.getWalletId())
                    .vnfAccountNo(wallet != null ? wallet.getVnfAccountNo() : null)
                    .type(transaction.getType())
                    .amount(transaction.getAmount())
                    .status(transaction.getStatus())
                    .description(transaction.getDescription())
                    .referenceId(transaction.getReferenceId())
                    .externalRef(transaction.getExternalRef())
                    .balanceAfter(transaction.getBalanceAfter())
                    .createdAt(transaction.getCreatedAt())
                    .build();
        });
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
                               BigDecimal runningBalance, String description) {
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
                .description(description != null && !description.isBlank() ? description : "Nạp tiền vào ví VNFITE")
                .balanceAfter(availableAfter)
                .build());

        log.info("txnId={} Deposit processed: accNo={} amount={} totalAfter={} availableAfter={}",
                txnId, accNo, amount, totalAfter, availableAfter);

        publishDepositEvent(wallet.getUserId(), amount, availableAfter, txn.getId());
    }

    // ─── Balance lock/unlock (dùng khi đầu tư) ────────────────────────────────

    @Transactional
    public void lockAmount(String userId, BigDecimal amount, String description) {
        lockAmount(userId, amount, description, null);
    }

    @Transactional
    public void lockAmount(String userId, BigDecimal amount, String description, String referenceId) {
        if (referenceId != null && transactionRepository.existsByReferenceId(referenceId)) {
            log.warn("Idempotent lock skip: referenceId={} đã xử lý", referenceId);
            return;
        }

        Wallet wallet = walletRepository.findWithLockByUserIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new IllegalStateException("Wallet not found for user " + userId));
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
                .referenceId(referenceId)
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
     * Người gọi vốn nhận tiền giải ngân vào ví VNF (tổng từ các nhà đầu tư sau khi CMS disburse).
     * Idempotent theo referenceId (vd "CREDIT-BORROWER-{loanId}") chống cộng trùng khi retry.
     */
    @Transactional
    public void creditDisbursement(String userId, BigDecimal amount, String description, String referenceId) {
        if (referenceId != null && transactionRepository.existsByReferenceId(referenceId)) {
            log.warn("Idempotent disbursement-credit skip: referenceId={} đã xử lý", referenceId);
            return;
        }

        Wallet wallet = findByUser(userId);
        boolean mock = appProperties.getPayment().isMock();

        if (!mock) {
            String tikluyTxnId = referenceId != null ? referenceId : "DISBURSE-IN-" + UUID.randomUUID();
            tikluyClient.topUpAccount(tikluyTxnId, wallet.getVnfAccountNo(), amount);
        }

        transactionRepository.save(WalletTransaction.builder()
                .walletId(wallet.getId())
                .type(TransactionType.DISBURSEMENT)
                .amount(amount)
                .status(TransactionStatus.SUCCESS)
                .referenceId(referenceId)
                .description(description != null ? description : "Nhận tiền giải ngân")
                .balanceAfter(computeAvailable(wallet))
                .build());
    }

    /**
     * Nhà đầu tư nhận tiền hoàn trả (gốc + lãi) khi người gọi vốn trả nợ.
     *
     * <p>Đây là vế CỘNG của giao dịch chuyển nội bộ: tiền vừa bị trừ khỏi VA người gọi vốn
     * (xem {@link #debitRepayment}) được cộng vào VA nhà đầu tư trên TIKLUY → số dư khả dụng tăng.
     * Idempotent theo {@code referenceId} (vd "REPAY-IN-{...}-{offerId}") chống cộng trùng khi retry.
     */
    @Transactional
    public void creditRepayment(String userId, BigDecimal amount, String description, String referenceId) {
        if (referenceId != null && transactionRepository.existsByReferenceId(referenceId)) {
            log.warn("Idempotent repay-credit skip: referenceId={} đã xử lý", referenceId);
            return;
        }

        Wallet wallet = findByUser(userId);
        boolean mock = appProperties.getPayment().isMock();

        // Cộng TIKLUY TRƯỚC khi ghi DB: nếu cộng thất bại (ném) thì rollback, không ghi ledger ảo.
        if (!mock) {
            String tikluyTxnId = referenceId != null ? referenceId : "REPAY-IN-" + UUID.randomUUID();
            tikluyClient.topUpAccount(tikluyTxnId, wallet.getVnfAccountNo(), amount);
        }

        transactionRepository.save(WalletTransaction.builder()
                .walletId(wallet.getId())
                .type(TransactionType.REPAYMENT)
                .amount(amount)
                .status(TransactionStatus.SUCCESS)
                .referenceId(referenceId)
                .description(description != null ? description : "Nhận tiền hoàn trả")
                .balanceAfter(computeAvailable(wallet))
                .build());
    }

    /**
     * Người gọi vốn trả nợ — trừ tiền khỏi ví (vế TRỪ của chuyển nội bộ borrower → investors).
     * Giảm TOTAL_MONEY trên TIKLUY để số dư khả dụng giảm thật.
     *
     * <p><b>Fail-closed:</b> nếu số dư khả dụng không đủ thì ném {@link IllegalStateException}
     * (→ 4xx) để loan-service dừng luồng, không trả nợ khi ví thiếu tiền.
     * Idempotent theo {@code referenceId} (vd "REPAY-OUT-{loanId}-P{n}-{date}") chống trừ trùng.
     */
    @Transactional
    public void debitRepayment(String userId, BigDecimal amount, String description, String referenceId) {
        if (referenceId != null && transactionRepository.existsByReferenceId(referenceId)) {
            log.warn("Idempotent repay-debit skip: referenceId={} đã xử lý", referenceId);
            return;
        }

        Wallet wallet = findByUser(userId);
        boolean mock = appProperties.getPayment().isMock();
        BigDecimal tikluyTotal = mock ? BigDecimal.ZERO : getTikluyBalance(wallet);
        BigDecimal locked = wallet.getLockedBalance() != null ? wallet.getLockedBalance() : BigDecimal.ZERO;
        BigDecimal available = tikluyTotal.subtract(locked).max(BigDecimal.ZERO);

        // Mock mode: TIKLUY balance luôn = 0 nên bỏ qua kiểm tra để không chặn test cục bộ.
        if (!mock && available.compareTo(amount) < 0) {
            throw new IllegalStateException(
                    "Số dư khả dụng không đủ để trả nợ. Khả dụng: " + available + ", cần: " + amount);
        }

        BigDecimal balanceAfter = available.subtract(amount).max(BigDecimal.ZERO);
        transactionRepository.save(WalletTransaction.builder()
                .walletId(wallet.getId())
                .type(TransactionType.REPAY)
                .amount(amount)
                .status(TransactionStatus.SUCCESS)
                .referenceId(referenceId)
                .description(description != null ? description : "Trả nợ khoản vay")
                .balanceAfter(balanceAfter)
                .build());

        // Trừ TIKLUY CUỐI CÙNG: không còn thao tác DB nào sau lời gọi ngoài này; TIKLUY lỗi → ném → rollback.
        if (!mock) {
            String tikluyTxnId = referenceId != null ? referenceId : "REPAY-OUT-" + UUID.randomUUID();
            tikluyClient.deductAccount(tikluyTxnId, wallet.getVnfAccountNo(), amount);
        }
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    public Wallet findByUser(String userId) {
        return walletRepository.findByUserIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new IllegalStateException(
                        "Wallet chưa được tạo cho user " + userId + ". KYC cần được duyệt trước."));
    }

    /** Số dư thực từ TIKLUY (MB Bank). Mock mode trả 0. */
    public BigDecimal getTikluyBalance(Wallet wallet) {
        return getTikluyAccountInfo(wallet).getTotalMoney();
    }

    private TikluyClient.TikluyAccountInfo getTikluyAccountInfo(Wallet wallet) {
        if (appProperties.getPayment().isMock()) {
            return TikluyClient.TikluyAccountInfo.builder()
                    .totalMoney(BigDecimal.ZERO)
                    .lockedMoney(BigDecimal.ZERO)
                    .build();
        }
        try {
            return tikluyClient.getAccount(
                    "BAL-" + wallet.getId().substring(0, 8), wallet.getVnfAccountNo());
        } catch (Exception e) {
            log.warn("Không lấy được balance từ TIKLUY accNo={}: {}", wallet.getVnfAccountNo(), e.getMessage());
            return TikluyClient.TikluyAccountInfo.builder()
                    .totalMoney(BigDecimal.ZERO)
                    .lockedMoney(BigDecimal.ZERO)
                    .build();
        }
    }

    /**
     * Trong thời gian 6666 và hệ thống mới chạy song song, dữ liệu migrate có thể làm
     * provider lockedMoney và local lockedBalance cùng biểu diễn một khoản khóa cũ.
     * Dùng max thay vì cộng để không double-count, đồng thời vẫn tôn trọng khóa mới nhất
     * ở một trong hai hệ thống.
     */
    public BigDecimal computeAvailable(Wallet wallet) {
        TikluyClient.TikluyAccountInfo info = getTikluyAccountInfo(wallet);
        return computeAvailableFromProvider(info, wallet);
    }

    private BigDecimal computeAvailableFromTotal(BigDecimal totalBalance, Wallet wallet) {
        BigDecimal total = totalBalance != null ? totalBalance : BigDecimal.ZERO;
        BigDecimal locked = wallet.getLockedBalance() != null ? wallet.getLockedBalance() : BigDecimal.ZERO;
        return total.subtract(locked).max(BigDecimal.ZERO);
    }

    private BigDecimal computeAvailableFromProvider(TikluyClient.TikluyAccountInfo info,
                                                    Wallet wallet) {
        BigDecimal total = info.getTotalMoney() != null
                ? info.getTotalMoney() : BigDecimal.ZERO;
        BigDecimal providerLocked = info.getLockedMoney() != null
                ? info.getLockedMoney() : BigDecimal.ZERO;
        BigDecimal localLocked = wallet.getLockedBalance() != null
                ? wallet.getLockedBalance() : BigDecimal.ZERO;
        return total.subtract(providerLocked.max(localLocked)).max(BigDecimal.ZERO);
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
        TikluyClient.TikluyAccountInfo info = getTikluyAccountInfo(w);
        BigDecimal total = info.getTotalMoney() != null
                ? info.getTotalMoney() : BigDecimal.ZERO;
        BigDecimal available = computeAvailableFromProvider(info, w);
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
        return toTxnResponse(t, t.getBalanceAfter());
    }

    private TransactionResponse toTxnResponse(WalletTransaction t, BigDecimal balanceAfter) {
        return TransactionResponse.builder()
                .id(t.getId())
                .type(t.getType())
                .amount(t.getAmount())
                .status(t.getStatus())
                .description(t.getDescription())
                .balanceAfter(balanceAfter)
                .createdAt(t.getCreatedAt())
                .build();
    }

    private BigDecimal signedAvailableDelta(WalletTransaction transaction) {
        if (transaction.getAmount() == null || transaction.getType() == null) {
            return BigDecimal.ZERO;
        }
        TransactionStatus status = transaction.getStatus();
        TransactionType type = transaction.getType();

        if (status == TransactionStatus.FAILED) {
            return BigDecimal.ZERO;
        }
        if (status == TransactionStatus.PENDING && type != TransactionType.WITHDRAW) {
            return BigDecimal.ZERO;
        }

        return switch (type) {
            case DEPOSIT, INVEST_REFUND, DISBURSEMENT, REPAYMENT -> transaction.getAmount();
            case WITHDRAW, INVEST, REPAY -> transaction.getAmount().negate();
        };
    }
}
