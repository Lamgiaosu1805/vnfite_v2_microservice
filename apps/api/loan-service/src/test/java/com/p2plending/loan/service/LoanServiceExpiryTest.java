package com.p2plending.loan.service;

import com.p2plending.loan.client.AuthServiceClient;
import com.p2plending.loan.client.PaymentServiceClient;
import com.p2plending.loan.domain.entity.LoanRequest;
import com.p2plending.loan.domain.enums.LoanStatus;
import com.p2plending.loan.domain.repository.FeeRevenueLedgerRepository;
import com.p2plending.loan.domain.repository.LoanDocumentRepository;
import com.p2plending.loan.domain.repository.LoanOfferRepository;
import com.p2plending.loan.domain.repository.LoanRequestRepository;
import com.p2plending.loan.kafka.KafkaProducerService;
import com.p2plending.loan.mapper.LoanOfferMapper;
import com.p2plending.loan.mapper.LoanRequestMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoanServiceExpiryTest {

    @Mock LoanRequestRepository loanRequestRepository;
    @Mock LoanOfferRepository   loanOfferRepository;
    @Mock LoanDocumentRepository loanDocumentRepository;
    @Mock LoanRequestMapper     loanRequestMapper;
    @Mock LoanOfferMapper       loanOfferMapper;
    @Mock KafkaProducerService  kafkaProducerService;
    @Mock LoanProductService    loanProductService;
    @Mock RepaymentService      repaymentService;
    @Mock ContractService       contractService;
    @Mock AuthServiceClient     authServiceClient;
    @Mock PaymentServiceClient  paymentServiceClient;
    @Mock FeeRevenueLedgerRepository feeRevenueLedgerRepository;
    @Mock CacheManager          cacheManager;

    @InjectMocks LoanService loanService;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(loanService, "fundingWindowDays", 180);
        ReflectionTestUtils.setField(loanService, "signingWindowDays", 7);
        ReflectionTestUtils.setField(loanService, "borrowerConfirmationWindowDays", 7);
    }

    @Test
    void findExpiredAwaitingBorrowerApprovalLoanIds_queriesByReviewedAtCutoff() {
        LoanRequest stuck = LoanRequest.builder().id("loan-stuck").build();
        when(loanRequestRepository.findByStatusAndReviewedAtBeforeAndIsDeletedFalse(
                eq(LoanStatus.AWAITING_BORROWER_APPROVAL), any(LocalDateTime.class)))
                .thenReturn(List.of(stuck));

        List<String> ids = loanService.findExpiredAwaitingBorrowerApprovalLoanIds();

        assertThat(ids).containsExactly("loan-stuck");

        ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        org.mockito.Mockito.verify(loanRequestRepository).findByStatusAndReviewedAtBeforeAndIsDeletedFalse(
                eq(LoanStatus.AWAITING_BORROWER_APPROVAL), cutoffCaptor.capture());
        LocalDateTime expectedCutoff = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).minusDays(7);
        assertThat(cutoffCaptor.getValue()).isCloseTo(expectedCutoff, within(2, java.time.temporal.ChronoUnit.SECONDS));
    }


    @Test
    void expireAndRefund_cancelsAwaitingBorrowerApprovalLoan() {
        LoanRequest loan = LoanRequest.builder()
                .id("loan-stuck")
                .status(LoanStatus.AWAITING_BORROWER_APPROVAL)
                .build();
        when(loanRequestRepository.findById("loan-stuck")).thenReturn(java.util.Optional.of(loan));
        when(loanRequestRepository.save(any(LoanRequest.class))).thenAnswer(i -> i.getArgument(0));

        loanService.expireAndRefund("loan-stuck", LoanStatus.AWAITING_BORROWER_APPROVAL,
                FundingExpiryService.REASON_BORROWER_CONFIRMATION_EXPIRED);

        assertThat(loan.getStatus()).isEqualTo(LoanStatus.CANCELLED);
        assertThat(loan.getBorrowerCancelledReason())
                .isEqualTo(FundingExpiryService.REASON_BORROWER_CONFIRMATION_EXPIRED);
        org.mockito.Mockito.verify(contractService).refundInvestorsAndVoid(loan,
                FundingExpiryService.REASON_BORROWER_CONFIRMATION_EXPIRED);
    }
}
