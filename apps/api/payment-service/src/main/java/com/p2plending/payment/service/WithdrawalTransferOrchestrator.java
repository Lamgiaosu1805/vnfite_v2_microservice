package com.p2plending.payment.service;

import com.p2plending.payment.config.AppProperties;
import com.p2plending.payment.domain.entity.WithdrawalRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** Điều phối transaction DB và HTTP transfer; bản thân bean này không mở transaction. */
@Service
@RequiredArgsConstructor
@Slf4j
public class WithdrawalTransferOrchestrator {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final long PROVIDER_POLL_DELAY_MINUTES = 1;
    private static final long STUCK_THRESHOLD_MINUTES = 30;

    private final WithdrawalRequestService withdrawalService;
    private final TikluyClient tikluyClient;
    private final AppProperties appProperties;

    public WithdrawalRequest confirmOtp(String userId, String withdrawalId, String otp) {
        WithdrawalRequestService.TransferAttempt attempt =
                withdrawalService.prepareConfirmedTransfer(userId, withdrawalId, otp);
        dispatch(attempt);
        return withdrawalService.getForUser(userId, withdrawalId);
    }

    public void handleTransferCallback(String transferRef, String providerTransferRef, boolean success,
                                       String ftNumber, String errorCode) {
        withdrawalService.attachProviderReference(transferRef, providerTransferRef);
        String lookupReference = providerTransferRef != null && !providerTransferRef.isBlank()
                ? providerTransferRef : transferRef;
        var pending = withdrawalService.findPendingTransferByReference(lookupReference);
        if (pending.isEmpty()) {
            // 8888 chỉ relay lệnh có clientReference của hệ thống mới. Vẫn xử lý
            // idempotent trường hợp callback lặp/đã hoàn tất: trả OK, không tác động.
            log.info("withdrawal.callback.ignored reference={} providerRef={} (legacy hoặc đã xử lý)",
                    transferRef, providerTransferRef);
            return;
        }
        if (success) {
            completeSuccessfulTransfer(pending.get(), ftNumber);
            return;
        }
        withdrawalService.processTransferCallback(lookupReference, false, ftNumber, errorCode)
                .ifPresent(this::dispatch);
    }

    public void retryTransfer(String adminId, String withdrawalId) {
        dispatch(withdrawalService.prepareManualRetry(adminId, withdrawalId));
    }

    @Scheduled(fixedDelay = 5 * 60 * 1000)
    public void reconcileStuck() {
        LocalDateTime now = LocalDateTime.now(VIETNAM_ZONE);
        LocalDateTime providerPollThreshold = now.minusMinutes(PROVIDER_POLL_DELAY_MINUTES);
        var pendingProviders = withdrawalService
                .findPendingProviderTransfers(providerPollThreshold);
        for (var pending : pendingProviders) {
            reconcileProviderStatus(pending);
        }

        LocalDateTime threshold = now
                .minusMinutes(STUCK_THRESHOLD_MINUTES);
        var stuckIds = withdrawalService.findStuckWithdrawalIds(threshold);
        if (stuckIds.isEmpty()) {
            return;
        }

        log.warn("withdrawal.reconcile found {} stuck withdrawal(s)", stuckIds.size());
        for (String withdrawalId : stuckIds) {
            try {
                withdrawalService.prepareStuckRetry(withdrawalId, threshold)
                        .ifPresent(this::dispatch);
            } catch (Exception ex) {
                log.error("withdrawal.reconcile error withdrawalId={}: {}",
                        withdrawalId, ex.getMessage(), ex);
            }
        }
    }

    private void dispatch(WithdrawalRequestService.TransferAttempt attempt) {
        if (appProperties.getPayment().isMock()) {
            String mockFt = "MOCK_FT_" + attempt.withdrawalId().substring(0, 8);
            withdrawalService.recordTransferAccepted(
                    attempt.withdrawalId(), attempt.transferRef(), attempt.transferRef());
            withdrawalService.processTransferCallback(
                    attempt.transferRef(), true, mockFt, null);
            return;
        }

        try {
            TikluyClient.TransferInitiation initiation = tikluyClient.fundTransfer(
                    attempt.transferRef(),
                    attempt.vnfAccountNo(),
                    attempt.bankCode(),
                    attempt.bankAccountNo(),
                    attempt.amount().toPlainString(),
                    attempt.source());
            withdrawalService.recordTransferAccepted(
                    attempt.withdrawalId(), attempt.transferRef(), initiation.providerReference());
            var pending = withdrawalService
                    .findPendingTransferByReference(initiation.providerReference());
            if (pending.isEmpty()) {
                return;
            }
            if (initiation.state() == TikluyClient.TransferState.SUCCESS) {
                completeSuccessfulTransfer(pending.get(), initiation.ftNumber());
            } else if (initiation.state() == TikluyClient.TransferState.FAILED) {
                withdrawalService.processTransferCallback(
                                initiation.providerReference(), false,
                                initiation.ftNumber(), initiation.errorCode())
                        .ifPresent(this::dispatch);
            }
        } catch (Exception ex) {
            String errorMessage = ex.getMessage() != null
                    ? ex.getMessage()
                    : ex.getClass().getSimpleName();
            boolean definitelyNotSent = isDefinitelyNotSent(ex);
            String errorCode = extractErrorCode(ex, errorMessage);
            withdrawalService.recordTransferDispatchFailure(
                            attempt.withdrawalId(), attempt.transferRef(), errorMessage,
                            definitelyNotSent, errorCode)
                    .ifPresent(this::dispatch);
        }
    }

    private void reconcileProviderStatus(WithdrawalRequestService.PendingTransfer pending) {
        try {
            TikluyClient.TransferQueryResult result = tikluyClient.getTransferStatus(
                    "WITHDRAW-STATUS-" + pending.withdrawalId(),
                    pending.providerTransferRef());
            if (result.state() == TikluyClient.TransferState.SUCCESS) {
                completeSuccessfulTransfer(pending, result.ftNumber());
            } else if (result.state() == TikluyClient.TransferState.FAILED) {
                withdrawalService.processTransferCallback(
                                pending.providerTransferRef(), false,
                                result.ftNumber(), result.errorCode())
                        .ifPresent(this::dispatch);
            } else {
                log.debug("withdrawal.reconcile.processing withdrawalId={} providerRef={} status={}",
                        pending.withdrawalId(), pending.providerTransferRef(), result.rawStatus());
            }
        } catch (Exception ex) {
            // Query lỗi/timeout chỉ giữ PROCESSING; tuyệt đối không retry lệnh chuyển tiền.
            log.warn("withdrawal.reconcile.queryFailed withdrawalId={} providerRef={}: {}",
                    pending.withdrawalId(), pending.providerTransferRef(), ex.getMessage());
        }
    }

    private void completeSuccessfulTransfer(WithdrawalRequestService.PendingTransfer pending,
                                            String ftNumber) {
        // TIKLUY 8888 là gateway được MB whitelist. Quyết toán VA qua endpoint idempotent;
        // nếu DB payment-service lỗi sau bước này, lần gọi lại dùng cùng settlement ref và
        // TIKLUY không trừ lần hai.
        tikluyClient.settleWithdrawal(
                "WITHDRAW-SETTLE-" + pending.withdrawalId(),
                pending.vnfAccountNo(), pending.amount());
        String callbackReference = pending.providerTransferRef() != null
                ? pending.providerTransferRef()
                : pending.transferRef();
        withdrawalService.processTransferCallback(
                        callbackReference, true, ftNumber, null)
                .ifPresent(this::dispatch);
    }

    private boolean isDefinitelyNotSent(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof ConnectException
                    || current instanceof NoRouteToHostException
                    || current instanceof UnknownHostException
                    || current instanceof TikluyClient.TikluyBusinessException
                    || current instanceof HttpClientErrorException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String extractErrorCode(Throwable error, String message) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof TikluyClient.TikluyBusinessException businessException) {
                return businessException.getErrorCode();
            }
            current = current.getCause();
        }
        if (message == null) {
            return null;
        }
        int colon = message.indexOf(':');
        String candidate = (colon > 0 ? message.substring(0, colon) : message)
                .trim()
                .toUpperCase();
        return candidate.matches("[A-Z][A-Z0-9_]{1,49}") ? candidate : null;
    }
}
