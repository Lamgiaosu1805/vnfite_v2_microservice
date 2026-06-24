package com.p2plending.payment.service;

import com.p2plending.payment.domain.entity.ReconciliationItem;
import com.p2plending.payment.domain.entity.ReconciliationSession;
import com.p2plending.payment.domain.entity.WithdrawalRequest;
import com.p2plending.payment.domain.entity.WalletTransaction;
import com.p2plending.payment.domain.enums.TransactionStatus;
import com.p2plending.payment.domain.enums.TransactionType;
import com.p2plending.payment.domain.enums.WithdrawalStatus;
import com.p2plending.payment.domain.repository.ReconciliationItemRepository;
import com.p2plending.payment.domain.repository.ReconciliationSessionRepository;
import com.p2plending.payment.domain.repository.WalletTransactionRepository;
import com.p2plending.payment.domain.repository.WithdrawalRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

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
    private final WithdrawalRequestRepository withdrawalRepo;
    private final TikluyClient tikluyClient;

    @Transactional
    public ReconciliationSession runReconciliation(LocalDate reconDate, String runBy) {
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

            // 1. Stale deposits (PENDING quá STALE_DEPOSIT_HOURS giờ)
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

            // 2. Stale withdrawals (kẹt trong trạng thái đang xử lý quá STALE_WITHDRAWAL_HOURS giờ)
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

            // 3. Kiểm tra trạng thái thực tế tại MB qua TIKLUY cho các lệnh rút trong ngày tra soát
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
            session.setOpenItems(items.size());
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
}
