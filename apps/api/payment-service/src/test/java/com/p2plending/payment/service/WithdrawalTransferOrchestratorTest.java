package com.p2plending.payment.service;

import com.p2plending.payment.config.AppProperties;
import com.p2plending.payment.domain.entity.WithdrawalRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WithdrawalTransferOrchestratorTest {

    @Mock
    private WithdrawalRequestService withdrawalService;
    @Mock
    private TikluyClient tikluyClient;

    private WithdrawalTransferOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties();
        properties.getPayment().setMock(false);
        orchestrator = new WithdrawalTransferOrchestrator(
                withdrawalService, tikluyClient, properties);
    }

    @Test
    void ambiguousTimeoutDoesNotRetryOrDoubleSend() {
        var attempt = attempt("withdrawal-1", "withdrawal-1-R0");
        var withdrawal = WithdrawalRequest.builder().id("withdrawal-1").build();
        when(withdrawalService.prepareConfirmedTransfer("user-1", "withdrawal-1", "000000"))
                .thenReturn(attempt);
        when(tikluyClient.fundTransfer(
                attempt.transferRef(), attempt.vnfAccountNo(), attempt.bankCode(),
                attempt.bankAccountNo(), attempt.amount().toPlainString(), attempt.source()))
                .thenThrow(new RuntimeException("Read timed out", new SocketTimeoutException("Read timed out")));
        when(withdrawalService.recordTransferDispatchFailure(
                attempt.withdrawalId(), attempt.transferRef(), "Read timed out", false, null))
                .thenReturn(Optional.empty());
        when(withdrawalService.getForUser("user-1", "withdrawal-1"))
                .thenReturn(withdrawal);

        orchestrator.confirmOtp("user-1", "withdrawal-1", "000000");

        InOrder order = inOrder(withdrawalService, tikluyClient);
        order.verify(withdrawalService)
                .prepareConfirmedTransfer("user-1", "withdrawal-1", "000000");
        order.verify(tikluyClient).fundTransfer(
                attempt.transferRef(), attempt.vnfAccountNo(), attempt.bankCode(),
                attempt.bankAccountNo(), attempt.amount().toPlainString(), "VNFITE");
        order.verify(withdrawalService).recordTransferDispatchFailure(
                attempt.withdrawalId(), attempt.transferRef(), "Read timed out", false, null);
        verify(tikluyClient, times(1)).fundTransfer(any(), any(), any(), any(), any(), eq("VNFITE"));
        verify(withdrawalService, never()).recordTransferAccepted(any(), any(), any());
    }

    @Test
    void definitelyNotSentFailureCanDispatchPreparedRetry() {
        var first = attempt("withdrawal-2", "withdrawal-2-R0");
        var retry = attempt("withdrawal-2", "withdrawal-2-R1");
        var withdrawal = WithdrawalRequest.builder().id("withdrawal-2").build();
        when(withdrawalService.prepareConfirmedTransfer("user-2", "withdrawal-2", "000000"))
                .thenReturn(first);
        when(tikluyClient.fundTransfer(
                first.transferRef(), first.vnfAccountNo(), first.bankCode(),
                first.bankAccountNo(), first.amount().toPlainString(), first.source()))
                .thenThrow(new RuntimeException("Connection refused", new ConnectException("Connection refused")));
        when(withdrawalService.recordTransferDispatchFailure(
                first.withdrawalId(), first.transferRef(), "Connection refused", true, null))
                .thenReturn(Optional.of(retry));
        when(tikluyClient.fundTransfer(
                retry.transferRef(), retry.vnfAccountNo(), retry.bankCode(),
                retry.bankAccountNo(), retry.amount().toPlainString(), retry.source()))
                .thenReturn(new TikluyClient.TransferInitiation(
                        "provider-ref", TikluyClient.TransferState.PROCESSING,
                        "PROCESSING", "", "000"));
        when(withdrawalService.getForUser("user-2", "withdrawal-2"))
                .thenReturn(withdrawal);

        orchestrator.confirmOtp("user-2", "withdrawal-2", "000000");

        verify(tikluyClient).fundTransfer(
                eq(first.transferRef()), any(), any(), any(), any(), eq("VNFITE"));
        verify(tikluyClient).fundTransfer(
                eq(retry.transferRef()), any(), any(), any(), any(), eq("VNFITE"));
        verify(withdrawalService).recordTransferAccepted(
                retry.withdrawalId(), retry.transferRef(), "provider-ref");
    }

    @Test
    void callbackFromLegacy6666IsIgnoredWithoutTouchingBalance() {
        when(withdrawalService.findPendingTransferByReference("YFCH-LEGACY"))
                .thenReturn(Optional.empty());

        orchestrator.handleTransferCallback(
                "legacy-client-ref", "YFCH-LEGACY", true, "FT-OLD", "000");

        verify(tikluyClient, never()).settleWithdrawal(any(), any(), any());
        verify(withdrawalService, never())
                .processTransferCallback(any(), anyBoolean(), any(), any());
    }

    @Test
    void immediateSuccessSettlesVaBeforeCompletingLocalWithdrawal() {
        var attempt = attempt("withdrawal-3", "withdrawal-3-R0");
        var pending = new WithdrawalRequestService.PendingTransfer(
                "withdrawal-3", "withdrawal-3-R0", "YFCH123",
                "VNF0000000001", new BigDecimal("100000"));
        var withdrawal = WithdrawalRequest.builder().id("withdrawal-3").build();
        when(withdrawalService.prepareConfirmedTransfer("user-3", "withdrawal-3", "000000"))
                .thenReturn(attempt);
        when(tikluyClient.fundTransfer(
                attempt.transferRef(), attempt.vnfAccountNo(), attempt.bankCode(),
                attempt.bankAccountNo(), attempt.amount().toPlainString(), attempt.source()))
                .thenReturn(new TikluyClient.TransferInitiation(
                        "YFCH123", TikluyClient.TransferState.SUCCESS,
                        "SUCCESS", "FT123", "000"));
        when(withdrawalService.findPendingTransferByReference("YFCH123"))
                .thenReturn(Optional.of(pending));
        when(withdrawalService.processTransferCallback("YFCH123", true, "FT123", null))
                .thenReturn(Optional.empty());
        when(withdrawalService.getForUser("user-3", "withdrawal-3"))
                .thenReturn(withdrawal);

        orchestrator.confirmOtp("user-3", "withdrawal-3", "000000");

        InOrder moneyOrder = inOrder(tikluyClient, withdrawalService);
        moneyOrder.verify(tikluyClient).settleWithdrawal(
                "WITHDRAW-SETTLE-withdrawal-3", "VNF0000000001", new BigDecimal("100000"));
        moneyOrder.verify(withdrawalService)
                .processTransferCallback("YFCH123", true, "FT123", null);
    }

    private WithdrawalRequestService.TransferAttempt attempt(String withdrawalId, String transferRef) {
        return new WithdrawalRequestService.TransferAttempt(
                withdrawalId,
                transferRef,
                "VNF0000000001",
                "MB",
                "0123456789",
                new BigDecimal("100000"),
                "VNFITE");
    }
}
