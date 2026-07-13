package com.p2plending.loan.service;

import com.p2plending.loan.client.AuthServiceClient;
import com.p2plending.loan.client.PaymentServiceClient;
import com.p2plending.loan.domain.entity.LoanProduct;
import com.p2plending.loan.domain.entity.LoanRequest;
import com.p2plending.loan.domain.enums.LoanStatus;
import com.p2plending.loan.domain.enums.ProductCategory;
import com.p2plending.loan.domain.repository.FeeRevenueLedgerRepository;
import com.p2plending.loan.domain.repository.LoanDocumentRepository;
import com.p2plending.loan.domain.repository.LoanOfferRepository;
import com.p2plending.loan.domain.repository.LoanRequestRepository;
import com.p2plending.loan.dto.request.LoanCreateRequest;
import com.p2plending.loan.dto.response.LoanResponse;
import com.p2plending.loan.exception.InvalidLoanStateException;
import com.p2plending.loan.kafka.KafkaProducerService;
import com.p2plending.loan.mapper.LoanOfferMapper;
import com.p2plending.loan.mapper.LoanRequestMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Sau khi bị từ chối, người gọi vốn phải chờ 14 ngày mới được tạo khoản mới. */
@ExtendWith(MockitoExtension.class)
class LoanServiceCreateResubmitTest {

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
    @Mock KycGuardService       kycGuardService;

    @InjectMocks LoanService loanService;

    private LoanProduct individualProduct() {
        return LoanProduct.builder()
                .id("product-1")
                .code("PRD1")
                .category(ProductCategory.INDIVIDUAL)
                .minAmount(BigDecimal.valueOf(1_000_000))
                .maxAmount(BigDecimal.valueOf(100_000_000))
                .availableTerms("6,12")
                .isActive(true)
                .build();
    }

    private LoanCreateRequest request() {
        LoanCreateRequest req = new LoanCreateRequest();
        req.setProductCode("PRD1");
        req.setAmount(BigDecimal.valueOf(10_000_000));
        req.setTermMonths(6);
        return req;
    }

    private LoanRequest rejectedLoan(LocalDateTime reviewedAt) {
        return LoanRequest.builder()
                .id("loan-rejected")
                .status(LoanStatus.REJECTED)
                .reviewedAt(reviewedAt)
                .build();
    }

    @Test
    void createLoan_blocksResubmit_within14DaysOfRejection() {
        when(loanProductService.findByCodeOrThrow("PRD1")).thenReturn(individualProduct());
        when(loanRequestRepository.existsByBorrowerIdAndStatusInAndIsDeletedFalse(anyString(), any()))
                .thenReturn(false);
        when(loanRequestRepository.findTopByBorrowerIdAndStatusAndIsDeletedFalseOrderByReviewedAtDesc(
                anyString(), any(LoanStatus.class)))
                .thenReturn(Optional.of(rejectedLoan(
                        LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).minusDays(5))));

        assertThatThrownBy(() -> loanService.createLoan(request(), "borrower-1"))
                .isInstanceOf(InvalidLoanStateException.class)
                .hasMessageContaining("ngày nữa");

        verify(loanRequestRepository, never()).saveAndFlush(any());
    }

    @Test
    void createLoan_allowsResubmit_after14DaysOfRejection() {
        when(loanProductService.findByCodeOrThrow("PRD1")).thenReturn(individualProduct());
        when(loanRequestRepository.existsByBorrowerIdAndStatusInAndIsDeletedFalse(anyString(), any()))
                .thenReturn(false);
        when(loanRequestRepository.findTopByBorrowerIdAndStatusAndIsDeletedFalseOrderByReviewedAtDesc(
                anyString(), any(LoanStatus.class)))
                .thenReturn(Optional.of(rejectedLoan(
                        LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).minusDays(15))));

        LoanRequest newLoan = LoanRequest.builder().id("loan-new").status(LoanStatus.PENDING_REVIEW).build();
        when(loanRequestMapper.toEntity(any())).thenReturn(newLoan);
        lenient().when(loanRequestRepository.findById("loan-new")).thenReturn(Optional.of(newLoan));
        lenient().when(loanRequestMapper.toResponse(newLoan)).thenReturn(LoanResponse.builder().build());
        lenient().when(loanProductService.findProductById(anyString())).thenReturn(Optional.empty());
        lenient().when(loanOfferRepository.countDistinctInvestorByLoanRequestIdAndStatus(anyString(), any()))
                .thenReturn(0L);

        LoanResponse response = loanService.createLoan(request(), "borrower-1");

        assertThat(response).isNotNull();
        verify(loanRequestRepository).saveAndFlush(any());
    }
}
