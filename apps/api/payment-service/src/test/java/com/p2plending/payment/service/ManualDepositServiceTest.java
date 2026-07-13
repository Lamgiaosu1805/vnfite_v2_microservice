package com.p2plending.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2plending.payment.domain.entity.ManualDepositRequest;
import com.p2plending.payment.domain.entity.Wallet;
import com.p2plending.payment.domain.enums.ManualDepositStatus;
import com.p2plending.payment.domain.enums.WalletOwnerType;
import com.p2plending.payment.domain.repository.ManualDepositRequestRepository;
import com.p2plending.payment.domain.repository.WalletRepository;
import com.p2plending.payment.domain.repository.WalletTransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManualDepositServiceTest {

    @Mock private ManualDepositRequestRepository requestRepository;
    @Mock private WalletRepository walletRepository;
    @Mock private WalletTransactionRepository walletTransactionRepository;
    @Mock private WalletService walletService;
    @Mock private TikluyClient tikluyClient;
    @Mock private KafkaTemplate<String, String> kafkaTemplate;
    @Mock private ObjectMapper objectMapper;
    @InjectMocks private ManualDepositService manualDepositService;

    @Test
    void approve_creditsWalletUsingStableIdempotencyReference() {
        ManualDepositRequest request = ManualDepositRequest.builder()
                .id("bill-1")
                .walletId("wallet-1")
                .userId("user-1")
                .ownerType(WalletOwnerType.PERSONAL)
                .amount(new BigDecimal("1250000"))
                .billFileId("file-1")
                .status(ManualDepositStatus.PENDING)
                .build();
        Wallet wallet = Wallet.builder().id("wallet-1").vnfAccountNo("VNF0000000001").build();
        when(requestRepository.findByIdForUpdate("bill-1")).thenReturn(Optional.of(request));
        when(walletRepository.findById("wallet-1")).thenReturn(Optional.of(wallet));
        when(requestRepository.save(any(ManualDepositRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        manualDepositService.approve("bill-1", "finance-1");

        verify(tikluyClient).creditManualDeposit("MANUAL-BILL-bill-1", "VNF0000000001", new BigDecimal("1250000"));
        verify(walletService).processDeposit("MANUAL-BILL-bill-1", "VNF0000000001", new BigDecimal("1250000"),
                "MANUAL-BILL-bill-1", null, "Nạp tiền theo bill đã được CMS phê duyệt");
        assertThat(request.getStatus()).isEqualTo(ManualDepositStatus.APPROVED);
        assertThat(request.getReviewedBy()).isEqualTo("finance-1");
    }

    @Test
    void reject_doesNotCreditWallet() throws Exception {
        ManualDepositRequest request = ManualDepositRequest.builder()
                .id("bill-2")
                .userId("user-2")
                .status(ManualDepositStatus.PENDING)
                .build();
        when(requestRepository.findByIdForUpdate("bill-2")).thenReturn(Optional.of(request));
        when(requestRepository.save(any(ManualDepositRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        manualDepositService.reject("bill-2", "finance-1", "Bill không khớp số tiền");

        verify(tikluyClient, never()).creditManualDeposit(any(), any(), any());
        verify(walletService, never()).processDeposit(any(), any(), any(), any(), any(), any());
        verify(kafkaTemplate).send("payment.manual_deposit_status", "user-2", "{}");
        assertThat(request.getStatus()).isEqualTo(ManualDepositStatus.REJECTED);
    }
}
