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

    public void handleTransferCallback(String transferRef, boolean success,
                                       String ftNumber, String errorCode) {
        withdrawalService.processTransferCallback(transferRef, success, ftNumber, errorCode)
                .ifPresent(this::dispatch);
    }

    public void retryTransfer(String adminId, String withdrawalId) {
        dispatch(withdrawalService.prepareManualRetry(adminId, withdrawalId));
    }

    @Scheduled(fixedDelay = 5 * 60 * 1000)
    public void reconcileStuck() {
        LocalDateTime threshold = LocalDateTime.now(VIETNAM_ZONE)
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
            String providerReference = tikluyClient.fundTransfer(
                    attempt.transferRef(),
                    attempt.vnfAccountNo(),
                    attempt.bankCode(),
                    attempt.bankAccountNo(),
                    attempt.amount().toPlainString());
            withdrawalService.recordTransferAccepted(
                    attempt.withdrawalId(), attempt.transferRef(), providerReference);
        } catch (Exception ex) {
            String errorMessage = ex.getMessage() != null
                    ? ex.getMessage()
                    : ex.getClass().getSimpleName();
            boolean definitelyNotSent = isDefinitelyNotSent(ex);
            String errorCode = extractErrorCode(errorMessage);
            withdrawalService.recordTransferDispatchFailure(
                            attempt.withdrawalId(), attempt.transferRef(), errorMessage,
                            definitelyNotSent, errorCode)
                    .ifPresent(this::dispatch);
        }
    }

    private boolean isDefinitelyNotSent(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof ConnectException
                    || current instanceof NoRouteToHostException
                    || current instanceof UnknownHostException
                    || current instanceof HttpClientErrorException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String extractErrorCode(String message) {
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
