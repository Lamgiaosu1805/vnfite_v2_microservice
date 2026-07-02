package com.p2plending.payment.service;

import com.p2plending.payment.config.AppProperties;
import com.p2plending.payment.domain.entity.ReconciliationItem;
import com.p2plending.payment.domain.entity.ReconciliationSession;
import com.p2plending.payment.domain.entity.WithdrawalRequest;
import com.p2plending.payment.domain.entity.Wallet;
import com.p2plending.payment.domain.entity.WalletTransaction;
import com.p2plending.payment.domain.enums.TransactionStatus;
import com.p2plending.payment.domain.enums.TransactionType;
import com.p2plending.payment.domain.enums.WithdrawalStatus;
import com.p2plending.payment.domain.repository.ReconciliationItemRepository;
import com.p2plending.payment.domain.repository.ReconciliationSessionRepository;
import com.p2plending.payment.domain.repository.WalletRepository;
import com.p2plending.payment.domain.repository.WalletTransactionRepository;
import com.p2plending.payment.domain.repository.WithdrawalRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReconciliationService {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int STALE_DEPOSIT_HOURS = 4;
    private static final int STALE_WITHDRAWAL_HOURS = 2;

    private final ReconciliationSessionRepository sessionRepo;
    private final ReconciliationItemRepository itemRepo;
    private final WalletTransactionRepository walletTxnRepo;
    private final WalletRepository walletRepo;
    private final WithdrawalRequestRepository withdrawalRepo;
    private final TikluyClient tikluyClient;
    private final WalletService walletService;
    private final AppProperties appProperties;

    @Transactional
    public ReconciliationSession runReconciliation(LocalDate reconDate, String runBy) {
        return runReconciliation(reconDate, runBy, false);
    }

    @Transactional
    public ReconciliationSession runReconciliation(LocalDate reconDate, String runBy, boolean autoFixDeposits) {
        ReconciliationSession session = sessionRepo.save(ReconciliationSession.builder()
                .reconDate(reconDate)
                .status("RUNNING")
                .runBy(runBy)
                .build());

        try {
            List<ReconciliationItem> items = new ArrayList<>();
            LocalDateTime now = LocalDateTime.now(VN_ZONE);
            LocalDateTime startOfDay = reconDate.atStartOfDay(VN_ZONE).toLocalDateTime();
            LocalDateTime endOfDay = reconDate.plusDays(1).atStartOfDay(VN_ZONE).toLocalDateTime();
            LocalDateTime staleDepositThreshold = now.minusHours(STALE_DEPOSIT_HOURS);
            LocalDateTime staleWithdrawalThreshold = now.minusHours(STALE_WITHDRAWAL_HOURS);

            // 1. TIKLUY đã nhận tiền nhưng VNFITE thiếu dòng biến động ví.
            items.addAll(findMissingTikluyDeposits(session.getId(), reconDate, autoFixDeposits, runBy));

            // 2. Stale deposits (PENDING quá STALE_DEPOSIT_HOURS giờ)
            List<WalletTransaction> staleDeposits = walletTxnRepo
                    .findByStatusAndTypeAndCreatedAtBeforeAndIsDeletedFalse(
                            TransactionStatus.PENDING, TransactionType.DEPOSIT, staleDepositThreshold);
            for (WalletTransaction dep : staleDeposits) {
                long hoursStuck = java.time.Duration.between(dep.getCreatedAt(), now).toHours();
                items.add(ReconciliationItem.builder()
                        .sessionId(session.getId())
                        .itemType("STALE_DEPOSIT")
                        .severity("MEDIUM")
                        .walletId(dep.getWalletId())
                        .transactionId(dep.getId())
                        .referenceId(dep.getReferenceId())
                        .vnfiteStatus(dep.getStatus().name())
                        .amount(dep.getAmount())
                        .description("Giao dịch nạp tiền PENDING đã " + hoursStuck + " giờ chưa xử lý. ReferenceId: " + dep.getReferenceId())
                        .build());
            }

            // 3. Stale withdrawals (kẹt trong trạng thái đang xử lý quá STALE_WITHDRAWAL_HOURS giờ)
            List<WithdrawalRequest> stuckWithdrawals = withdrawalRepo.findStuckWithdrawals(
                    List.of(WithdrawalStatus.FUNDS_LOCKED, WithdrawalStatus.TRANSFER_INITIATED,
                            WithdrawalStatus.PROCESSING, WithdrawalStatus.TRANSFER_FAILED),
                    staleWithdrawalThreshold);
            for (WithdrawalRequest wr : stuckWithdrawals) {
                long hoursStuck = java.time.Duration.between(wr.getUpdatedAt(), now).toHours();
                items.add(ReconciliationItem.builder()
                        .sessionId(session.getId())
                        .itemType("STALE_WITHDRAWAL")
                        .severity("HIGH")
                        .walletId(wr.getWalletId())
                        .transactionId(wr.getWalletTxnId())
                        .externalRef(wr.getTransferRef())
                        .vnfiteStatus(wr.getStatus().name())
                        .amount(wr.getAmount())
                        .description("Lệnh rút tiền kẹt ở trạng thái " + wr.getStatus() + " đã " + hoursStuck + " giờ. TransferRef: " + wr.getTransferRef())
                        .build());
            }

            // 4. Kiểm tra trạng thái thực tế tại MB qua TIKLUY cho các lệnh rút trong ngày tra soát
            List<WithdrawalRequest> todayWithdrawals = withdrawalRepo
                    .findByCreatedAtBetweenAndProviderTransferRefIsNotNullAndIsDeletedFalse(startOfDay, endOfDay);
            for (WithdrawalRequest wr : todayWithdrawals) {
                try {
                    String txnId = "RECON-" + wr.getId().substring(0, 8);
                    TikluyClient.TransferQueryResult result = tikluyClient.getTransferStatus(txnId, wr.getProviderTransferRef());
                    String mbStatusStr = result.state().name() + (result.rawStatus().isBlank() ? "" : " (" + result.rawStatus() + ")");

                    boolean vnfiteCompleted = wr.getStatus() == WithdrawalStatus.COMPLETED;
                    boolean vnfiteFailed = wr.getStatus() == WithdrawalStatus.FAILED
                            || wr.getStatus() == WithdrawalStatus.FUNDS_RELEASED;
                    boolean mbSuccess = result.state() == TikluyClient.TransferState.SUCCESS;
                    boolean mbFailed = result.state() == TikluyClient.TransferState.FAILED;

                    if (vnfiteFailed && mbSuccess) {
                        // Nghiêm trọng: VNFITE ghi FAILED nhưng MB đã chuyển tiền thực
                        items.add(ReconciliationItem.builder()
                                .sessionId(session.getId())
                                .itemType("FAILED_WITHDRAWAL_MB_SUCCESS")
                                .severity("HIGH")
                                .walletId(wr.getWalletId())
                                .transactionId(wr.getWalletTxnId())
                                .referenceId(wr.getProviderTransferRef())
                                .externalRef(wr.getTransferRef())
                                .vnfiteStatus(wr.getStatus().name())
                                .mbStatus(mbStatusStr)
                                .amount(wr.getAmount())
                                .description("Lệnh rút ghi FAILED trong VNFITE nhưng MB đã thực hiện thành công. FT: " + result.ftNumber() + ". Cần xác nhận số dư.")
                                .build());
                    } else if (!vnfiteCompleted && !vnfiteFailed && mbFailed) {
                        // VNFITE nghĩ đang xử lý nhưng MB đã báo thất bại
                        items.add(ReconciliationItem.builder()
                                .sessionId(session.getId())
                                .itemType("WITHDRAWAL_MB_MISMATCH")
                                .severity("HIGH")
                                .walletId(wr.getWalletId())
                                .transactionId(wr.getWalletTxnId())
                                .referenceId(wr.getProviderTransferRef())
                                .externalRef(wr.getTransferRef())
                                .vnfiteStatus(wr.getStatus().name())
                                .mbStatus(mbStatusStr)
                                .amount(wr.getAmount())
                                .description("MB báo thất bại nhưng VNFITE vẫn ghi trạng thái " + wr.getStatus() + ". Cần xử lý thủ công.")
                                .build());
                    } else if (vnfiteCompleted && mbFailed) {
                        // VNFITE nghĩ thành công nhưng MB báo thất bại
                        items.add(ReconciliationItem.builder()
                                .sessionId(session.getId())
                                .itemType("WITHDRAWAL_MB_MISMATCH")
                                .severity("HIGH")
                                .walletId(wr.getWalletId())
                                .transactionId(wr.getWalletTxnId())
                                .referenceId(wr.getProviderTransferRef())
                                .externalRef(wr.getTransferRef())
                                .vnfiteStatus(wr.getStatus().name())
                                .mbStatus(mbStatusStr)
                                .amount(wr.getAmount())
                                .description("VNFITE ghi COMPLETED nhưng MB báo thất bại. Cần kiểm tra lại số dư.")
                                .build());
                    }
                } catch (Exception e) {
                    log.warn("Không thể kiểm tra trạng thái MB cho withdrawal {}: {}", wr.getId(), e.getMessage());
                }
            }

            itemRepo.saveAll(items);

            session.setTotalItems(items.size());
            session.setOpenItems((int) items.stream()
                    .filter(i -> !"RESOLVED".equals(i.getStatus()))
                    .count());
            session.setStatus("COMPLETED");
            return sessionRepo.save(session);

        } catch (Exception e) {
            log.error("Reconciliation thất bại cho ngày {}: {}", reconDate, e.getMessage(), e);
            session.setStatus("FAILED");
            session.setErrorMessage(e.getMessage());
            return sessionRepo.save(session);
        }
    }

    @Transactional(readOnly = true)
    public Page<ReconciliationSession> listSessions(int page, int size) {
        return sessionRepo.findByIsDeletedFalseOrderByCreatedAtDesc(PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    public Page<ReconciliationItem> listItems(String sessionId, String status, int page, int size) {
        PageRequest pr = PageRequest.of(page, size);
        if (status == null || status.isBlank()) {
            return itemRepo.findBySessionIdAndIsDeletedFalseOrderByCreatedAtDesc(sessionId, pr);
        }
        return itemRepo.findBySessionIdAndStatusAndIsDeletedFalseOrderByCreatedAtDesc(sessionId, status, pr);
    }

    @Transactional
    public void resolveItem(String itemId, String resolvedBy, String notes) {
        int updated = itemRepo.resolve(itemId, "RESOLVED", resolvedBy, notes);
        if (updated == 0) {
            throw new IllegalArgumentException("Không tìm thấy reconciliation item: " + itemId);
        }
        // Cập nhật open_items trong session
        itemRepo.findById(itemId).ifPresent(item -> {
            int openItems = itemRepo.countOpenItems(item.getSessionId());
            sessionRepo.findById(item.getSessionId()).ifPresent(session -> {
                session.setOpenItems(openItems);
                sessionRepo.save(session);
            });
        });
    }

    @Transactional
    public void markItemInvestigating(String itemId, String updatedBy) {
        itemRepo.resolve(itemId, "INVESTIGATING", updatedBy, null);
    }

    @Transactional
    public void backfillMissingDeposit(String itemId, String resolvedBy) {
        ReconciliationItem item = itemRepo.findById(itemId)
                .filter(i -> !i.isDeleted())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy reconciliation item: " + itemId));
        if (!"MISSING_TIKLUY_DEPOSIT".equals(item.getItemType())) {
            throw new IllegalArgumentException("Chỉ có thể tự bù item nạp tiền thiếu từ TIKLUY.");
        }
        MissingDeposit deposit = findTikluyDepositByReference(item.getReferenceId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy giao dịch TIKLUY: " + item.getReferenceId()));
        backfillDeposit(deposit, resolvedBy);
        resolveItem(itemId, resolvedBy, "Đã tự động bù giao dịch nạp thiếu từ TIKLUY. FT=" + deposit.referenceId());
    }

    @Scheduled(cron = "${APP_RECON_AUTO_DEPOSIT_FIX_CRON:0 */10 * * * *}", zone = "Asia/Ho_Chi_Minh")
    public void autoFixRecentMissingDeposits() {
        if (!appProperties.getReconciliation().isAutoDepositFixEnabled()) {
            return;
        }
        try {
            runReconciliation(LocalDate.now(VN_ZONE), "system_auto", true);
        } catch (Exception e) {
            log.error("Auto reconciliation deposit fix failed: {}", e.getMessage(), e);
        }
    }

    private List<ReconciliationItem> findMissingTikluyDeposits(String sessionId, LocalDate reconDate,
                                                               boolean autoFix, String runBy) {
        List<ReconciliationItem> items = new ArrayList<>();
        for (MissingDeposit deposit : fetchTikluyDeposits(reconDate)) {
            if (deposit.referenceId() == null || walletTxnRepo.existsByReferenceId(deposit.referenceId())) {
                continue;
            }

            Optional<Wallet> walletOpt = walletRepo.findByVnfAccountNoAndIsDeletedFalse(deposit.accountNo().toUpperCase());
            ReconciliationItem item = ReconciliationItem.builder()
                    .sessionId(sessionId)
                    .itemType("MISSING_TIKLUY_DEPOSIT")
                    .severity("HIGH")
                    .walletId(walletOpt.map(Wallet::getId).orElse(null))
                    .referenceId(deposit.referenceId())
                    .externalRef(deposit.accountNo().toUpperCase())
                    .vnfiteStatus("MISSING")
                    .mbStatus("TIKLUY_RECEIVED")
                    .amount(deposit.amount())
                    .description("TIKLUY đã ghi nhận nạp tiền nhưng VNFITE chưa có biến động ví. "
                            + "VA=" + deposit.accountNo().toUpperCase()
                            + ", khách=" + deposit.customerName()
                            + ", lúc=" + deposit.transDate()
                            + ", nội dung=" + deposit.remark())
                    .build();

            if (autoFix && walletOpt.isPresent()) {
                try {
                    backfillDeposit(deposit, runBy);
                    item.setStatus("RESOLVED");
                    item.setResolvedBy(runBy);
                    item.setResolvedAt(LocalDateTime.now(VN_ZONE));
                    item.setResolutionNotes("Đã tự động bù giao dịch nạp thiếu từ TIKLUY.");
                } catch (Exception e) {
                    item.setDescription(item.getDescription() + ". Tự bù lỗi: " + e.getMessage());
                }
            }
            items.add(item);
        }
        return items;
    }

    private void backfillDeposit(MissingDeposit deposit, String actor) {
        BigDecimal runningBalance = deposit.runningBalance() != null ? deposit.runningBalance() : deposit.amount();
        String description = StringUtils.hasText(deposit.remark())
                ? deposit.remark()
                : "Tự động bù giao dịch nạp tiền từ TIKLUY bởi " + actor;
        walletService.processDeposit(
                deposit.referenceId(),
                deposit.accountNo().toUpperCase(),
                deposit.amount(),
                deposit.referenceId(),
                runningBalance,
                description);
    }

    private Optional<MissingDeposit> findTikluyDepositByReference(String referenceId) {
        if (!StringUtils.hasText(referenceId) || !tikluyDbConfigured()) {
            return Optional.empty();
        }
        String sql = """
                SELECT cb.REFERENCE_NUMBER, cb.CUSTOMER_ACC, cb.CUSTOMER_NAME, cb.AMOUNT,
                       cb.TRANS_DATE, cb.REMARK, bf.TOTAL_REMAINING_AMOUNT
                FROM tbl_callback_history cb
                LEFT JOIN tbl_account_balance_fluctuation bf
                  ON bf.TRANSACTION_ID = cb.REFERENCE_NUMBER
                WHERE cb.REFERENCE_NUMBER = ?
                LIMIT 1
                """;
        try (Connection conn = tikluyConnection();
             var ps = conn.prepareStatement(sql)) {
            ps.setString(1, referenceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(readMissingDeposit(rs));
                }
            }
        } catch (Exception e) {
            log.warn("Không thể đọc giao dịch TIKLUY referenceId={}: {}", referenceId, e.getMessage());
        }
        return Optional.empty();
    }

    private List<MissingDeposit> fetchTikluyDeposits(LocalDate reconDate) {
        if (!tikluyDbConfigured()) {
            log.debug("Bỏ qua đối soát nạp TIKLUY: chưa cấu hình APP_RECON_TIKLUY_DB_*");
            return List.of();
        }
        String sql = """
                SELECT cb.REFERENCE_NUMBER, cb.CUSTOMER_ACC, cb.CUSTOMER_NAME, cb.AMOUNT,
                       cb.TRANS_DATE, cb.REMARK, bf.TOTAL_REMAINING_AMOUNT
                FROM tbl_callback_history cb
                LEFT JOIN tbl_account_balance_fluctuation bf
                  ON bf.TRANSACTION_ID = cb.REFERENCE_NUMBER
                WHERE cb.CREATED_DATE >= ?
                  AND cb.CREATED_DATE < ?
                  AND UPPER(cb.CUSTOMER_ACC) LIKE 'VNF%'
                  AND cb.REFERENCE_NUMBER IS NOT NULL
                  AND cb.REFERENCE_NUMBER <> ''
                ORDER BY cb.CREATED_DATE ASC
                """;
        List<MissingDeposit> deposits = new ArrayList<>();
        try (Connection conn = tikluyConnection();
             var ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(reconDate.atStartOfDay()));
            ps.setTimestamp(2, Timestamp.valueOf(reconDate.plusDays(1).atStartOfDay()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    deposits.add(readMissingDeposit(rs));
                }
            }
        } catch (Exception e) {
            log.warn("Không thể đọc danh sách nạp TIKLUY ngày {}: {}", reconDate, e.getMessage());
        }
        return deposits;
    }

    private MissingDeposit readMissingDeposit(ResultSet rs) throws java.sql.SQLException {
        return new MissingDeposit(
                rs.getString("REFERENCE_NUMBER"),
                rs.getString("CUSTOMER_ACC"),
                rs.getString("CUSTOMER_NAME"),
                new BigDecimal(rs.getString("AMOUNT")).setScale(0, java.math.RoundingMode.HALF_UP),
                parseTikluyDateTime(rs.getString("TRANS_DATE")),
                rs.getString("REMARK"),
                rs.getBigDecimal("TOTAL_REMAINING_AMOUNT"));
    }

    private boolean tikluyDbConfigured() {
        var db = appProperties.getReconciliation().getTikluyDb();
        return StringUtils.hasText(db.getHost())
                && StringUtils.hasText(db.getUsername())
                && StringUtils.hasText(db.getPassword());
    }

    private Connection tikluyConnection() throws java.sql.SQLException {
        var db = appProperties.getReconciliation().getTikluyDb();
        String url = "jdbc:mysql://" + db.getHost() + ":" + db.getPort() + "/" + db.getDatabase()
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Ho_Chi_Minh&characterEncoding=UTF-8";
        return DriverManager.getConnection(url, db.getUsername(), db.getPassword());
    }

    private LocalDateTime parseTikluyDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return LocalDateTime.now(VN_ZONE);
        }
        try {
            return LocalDateTime.parse(value.replace(' ', 'T'));
        } catch (Exception ignored) {
            return LocalDateTime.now(VN_ZONE);
        }
    }

    private record MissingDeposit(
            String referenceId,
            String accountNo,
            String customerName,
            BigDecimal amount,
            LocalDateTime transDate,
            String remark,
            BigDecimal runningBalance) {
    }
}
