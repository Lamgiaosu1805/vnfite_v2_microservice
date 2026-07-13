package com.p2plending.loan.service;

import com.p2plending.loan.client.AuthServiceClient;
import com.p2plending.loan.client.PaymentServiceClient;
import com.p2plending.loan.domain.entity.LoanContract;
import com.p2plending.loan.domain.entity.LoanOffer;
import com.p2plending.loan.domain.entity.LoanRequest;
import com.p2plending.loan.domain.enums.ContractStatus;
import com.p2plending.loan.domain.enums.ContractType;
import com.p2plending.loan.domain.enums.LoanStatus;
import com.p2plending.loan.domain.enums.OfferStatus;
import com.p2plending.loan.domain.repository.LoanContractRepository;
import com.p2plending.loan.domain.repository.LoanOfferRepository;
import com.p2plending.loan.domain.repository.LoanRequestRepository;
import com.p2plending.loan.kafka.KafkaProducerService;
import com.p2plending.loan.service.contract.ContractSignatureProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContractServiceTest {

    @Mock private LoanContractRepository contractRepository;
    @Mock private LoanRequestRepository loanRequestRepository;
    @Mock private LoanOfferRepository loanOfferRepository;
    @Mock private ContractSignatureProvider signatureProvider;
    @Mock private KafkaProducerService kafkaProducerService;
    @Mock private PaymentServiceClient paymentServiceClient;
    @Mock private AuthServiceClient authServiceClient;
    @Mock private VnfOtpSenderService vnfOtpSenderService;
    @Mock private LoanProductService loanProductService;
    @Mock private CacheManager cacheManager;
    @InjectMocks private ContractService contractService;

    @Test
    void refundInvestorsAndVoid_voidsOnlyPendingSignature_keepsSignedContractsUntouched() {
        LoanRequest loan = LoanRequest.builder()
                .id("loan-1")
                .amount(new BigDecimal("10000000"))
                .build();

        LoanOffer acceptedOffer = LoanOffer.builder()
                .id("offer-1")
                .loanRequestId("loan-1")
                .investorId("investor-1")
                .ownerType("PERSONAL")
                .amount(new BigDecimal("5000000"))
                .status(OfferStatus.ACCEPTED)
                .build();
        when(loanOfferRepository.findByLoanRequestIdAndStatus("loan-1", OfferStatus.ACCEPTED))
                .thenReturn(List.of(acceptedOffer));
        when(loanOfferRepository.save(any(LoanOffer.class))).thenAnswer(i -> i.getArgument(0));

        LoanContract signedAgreement = LoanContract.builder()
                .id("contract-signed")
                .loanId("loan-1")
                .contractType(ContractType.LOAN_AGREEMENT)
                .status(ContractStatus.SIGNED)
                .build();
        LoanContract pendingInvestmentContract = LoanContract.builder()
                .id("contract-pending")
                .loanId("loan-1")
                .contractType(ContractType.INVESTMENT)
                .status(ContractStatus.PENDING_SIGNATURE)
                .build();
        when(contractRepository.findByLoanIdAndIsDeletedFalseOrderByCreatedAtDesc("loan-1"))
                .thenReturn(List.of(signedAgreement, pendingInvestmentContract));
        when(contractRepository.save(any(LoanContract.class))).thenAnswer(i -> i.getArgument(0));

        contractService.refundInvestorsAndVoid(loan, "Hết hạn ký khế ước");

        // Hợp đồng đã ký giữ nguyên trạng thái — không bị ghi đè thành VOIDED.
        assertThat(signedAgreement.getStatus()).isEqualTo(ContractStatus.SIGNED);
        // Hợp đồng chưa ký bị void vì offer đã được hoàn tiền/hủy.
        assertThat(pendingInvestmentContract.getStatus()).isEqualTo(ContractStatus.VOIDED);
        // Offer được hoàn tiền (unlock) và chuyển sang CANCELLED.
        assertThat(acceptedOffer.getStatus()).isEqualTo(OfferStatus.CANCELLED);
    }

    @Test
    void refundInvestorsAndVoid_unlocksFundsForEachAcceptedOffer() {
        LoanRequest loan = LoanRequest.builder().id("loan-2").amount(new BigDecimal("1000000")).build();
        LoanOffer offer = LoanOffer.builder()
                .id("offer-2")
                .loanRequestId("loan-2")
                .investorId("investor-2")
                .ownerType("BUSINESS")
                .amount(new BigDecimal("1000000"))
                .status(OfferStatus.ACCEPTED)
                .build();
        when(loanOfferRepository.findByLoanRequestIdAndStatus("loan-2", OfferStatus.ACCEPTED))
                .thenReturn(List.of(offer));
        when(loanOfferRepository.save(any(LoanOffer.class))).thenAnswer(i -> i.getArgument(0));
        when(contractRepository.findByLoanIdAndIsDeletedFalseOrderByCreatedAtDesc("loan-2"))
                .thenReturn(List.of());

        contractService.refundInvestorsAndVoid(loan, "Hết hạn gọi vốn");

        org.mockito.Mockito.verify(paymentServiceClient).unlock(
                eq("investor-2"), eq("BUSINESS"), eq(new BigDecimal("1000000")),
                anyString(), eq("REFUND-offer-2"));
    }

    @Test
    void confirmAllPaperSignatures_movesToAwaitingDisbursementOnlyAfterAllContractsAreSigned() {
        LoanRequest loan = LoanRequest.builder()
                .id("loan-3")
                .status(LoanStatus.FUNDED)
                .build();
        LoanContract borrowerContract = LoanContract.builder()
                .id("contract-borrower")
                .loanId("loan-3")
                .contractType(ContractType.LOAN_AGREEMENT)
                .status(ContractStatus.SIGNED)
                .build();
        LoanContract investorContract = LoanContract.builder()
                .id("contract-investor")
                .loanId("loan-3")
                .contractType(ContractType.INVESTMENT)
                .offerId("offer-3")
                .status(ContractStatus.PENDING_SIGNATURE)
                .build();
        LoanOffer acceptedOffer = LoanOffer.builder()
                .id("offer-3")
                .status(OfferStatus.ACCEPTED)
                .build();

        when(loanRequestRepository.findByIdForUpdate("loan-3")).thenReturn(java.util.Optional.of(loan));
        when(loanOfferRepository.findById("offer-3")).thenReturn(java.util.Optional.of(acceptedOffer));
        when(contractRepository.findByLoanIdAndIsDeletedFalseOrderByCreatedAtDesc("loan-3"))
                .thenReturn(List.of(investorContract, borrowerContract));
        when(loanRequestRepository.findById("loan-3")).thenReturn(java.util.Optional.of(loan));
        when(contractRepository.save(any(LoanContract.class))).thenAnswer(i -> i.getArgument(0));
        when(loanRequestRepository.save(any(LoanRequest.class))).thenAnswer(i -> i.getArgument(0));

        contractService.confirmAllPaperSignatures("loan-3", "CMS");

        assertThat(investorContract.getStatus()).isEqualTo(ContractStatus.SIGNED);
        assertThat(loan.getStatus()).isEqualTo(LoanStatus.AWAITING_DISBURSEMENT);
    }
}
