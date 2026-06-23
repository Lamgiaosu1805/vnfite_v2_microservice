package com.p2plending.loan.service;

import com.p2plending.loan.client.AuthServiceClient;
import com.p2plending.loan.client.PaymentServiceClient;
import com.p2plending.loan.domain.entity.LoanOffer;
import com.p2plending.loan.domain.entity.LoanProduct;
import com.p2plending.loan.domain.entity.LoanRequest;
import com.p2plending.loan.domain.enums.LoanStatus;
import com.p2plending.loan.domain.enums.OfferStatus;
import com.p2plending.loan.domain.repository.LoanDocumentRepository;
import com.p2plending.loan.domain.repository.LoanOfferRepository;
import com.p2plending.loan.domain.repository.LoanRequestRepository;
import com.p2plending.loan.dto.response.LoanResponse;
import com.p2plending.loan.exception.InvalidLoanStateException;
import com.p2plending.loan.kafka.KafkaProducerService;
import com.p2plending.loan.kafka.event.LoanReviewedEvent;
import com.p2plending.loan.mapper.LoanOfferMapper;
import com.p2plending.loan.mapper.LoanRequestMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoanServiceAppraisalFeeTest {

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
    @Mock CacheManager          cacheManager;

    @InjectMocks LoanService loanService;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(loanService, "fundingWindowDays", 30);
        ReflectionTestUtils.setField(loanService, "signingWindowDays", 7);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private LoanRequest pendingReviewLoan(String id) {
        LoanRequest loan = new LoanRequest();
        loan.setId(id);
        loan.setStatus(LoanStatus.PENDING_REVIEW);
        loan.setAmount(new BigDecimal("10000000"));
        loan.setBorrowerId("borrower-1");
        return loan;
    }

    private LoanRequest pendingApprovalLoan(String id, BigDecimal feeRate) {
        LoanRequest loan = pendingReviewLoan(id);
        loan.setStatus(LoanStatus.PENDING_APPROVAL);
        loan.setProposedAmount(new BigDecimal("10000000"));
        loan.setProposedInterestRate(new BigDecimal("12.00"));
        loan.setAppraisalFeeRate(feeRate);
        BigDecimal appraisalFee = loan.getProposedAmount()
                .multiply(feeRate).divide(new BigDecimal("100"), 0, java.math.RoundingMode.HALF_UP);
        BigDecimal vatAmount = appraisalFee.multiply(new BigDecimal("0.10"))
                .setScale(0, java.math.RoundingMode.HALF_UP);
        loan.setAppraisalFee(appraisalFee);
        loan.setVatAmount(vatAmount);
        loan.setTotalFee(appraisalFee.add(vatAmount));
        loan.setNetDisbursement(loan.getProposedAmount().subtract(loan.getTotalFee()));
        return loan;
    }

    private LoanResponse stubMapper(LoanRequest loan) {
        LoanResponse resp = LoanResponse.builder().id(loan.getId()).build();
        when(loanRequestMapper.toResponse(loan)).thenReturn(resp);
        when(loanRequestRepository.findById(loan.getId())).thenReturn(Optional.of(loan));
        return resp;
    }

    // ── 1. feeRate = 0.00 (miễn phí) ─────────────────────────────────────────

    @Test
    void proposeLoan_zeroFeeRate_setsZeroFeeFields() {
        LoanRequest loan = pendingReviewLoan("loan-1");
        stubMapper(loan);
        when(loanRequestRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        loanService.proposeLoan("loan-1", new BigDecimal("10000000"),
                new BigDecimal("12.00"), BigDecimal.ZERO, "ghi chú", "appraiser");

        assertThat(loan.getAppraisalFeeRate()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(loan.getAppraisalFee()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(loan.getVatAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(loan.getTotalFee()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(loan.getNetDisbursement()).isEqualByComparingTo(new BigDecimal("10000000"));
    }

    // ── 2. feeRate = 2% — số tiền chuẩn ──────────────────────────────────────

    @Test
    void proposeLoan_validFeeRate_calculatesCorrectly() {
        // proposedAmount = 10_000_000, rate = 2.00%
        // appraisalFee = 10_000_000 * 2 / 100 = 200_000
        // vatAmount    = 200_000 * 0.10 = 20_000
        // totalFee     = 220_000
        // netDisburse  = 9_780_000
        LoanRequest loan = pendingReviewLoan("loan-2");
        stubMapper(loan);
        when(loanRequestRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        loanService.proposeLoan("loan-2", new BigDecimal("10000000"),
                new BigDecimal("12.00"), new BigDecimal("2.00"), null, "appraiser");

        assertThat(loan.getAppraisalFee()).isEqualByComparingTo(new BigDecimal("200000"));
        assertThat(loan.getVatAmount()).isEqualByComparingTo(new BigDecimal("20000"));
        assertThat(loan.getTotalFee()).isEqualByComparingTo(new BigDecimal("220000"));
        assertThat(loan.getNetDisbursement()).isEqualByComparingTo(new BigDecimal("9780000"));
        assertThat(loan.getStatus()).isEqualTo(LoanStatus.PENDING_APPROVAL);
    }

    // ── 3. totalFee >= proposedAmount bị reject ───────────────────────────────

    @Test
    void proposeLoan_feeExceedsAmount_throwsError() {
        LoanRequest loan = pendingReviewLoan("loan-3");
        when(loanRequestRepository.findById("loan-3")).thenReturn(Optional.of(loan));

        // 100% fee → totalFee = amount + 10% VAT > amount
        assertThatThrownBy(() -> loanService.proposeLoan("loan-3",
                new BigDecimal("10000000"), new BigDecimal("12.00"),
                new BigDecimal("95.00"), null, "appraiser"))
                .isInstanceOf(InvalidLoanStateException.class)
                .hasMessageContaining("Tổng phí thẩm định");
    }

    // ── 4. Duyệt trực tiếp từ PENDING_REVIEW bị reject ───────────────────────

    @Test
    void applyLoanReview_approveFromPendingReview_throwsError() {
        LoanRequest loan = pendingReviewLoan("loan-4");
        when(loanRequestRepository.findById("loan-4")).thenReturn(Optional.of(loan));

        LoanReviewedEvent event = LoanReviewedEvent.builder()
                .loanId("loan-4").action("APPROVE").reviewedBy("admin")
                .reviewedAt(LocalDateTime.now()).build();

        assertThatThrownBy(() -> loanService.handleLoanReviewed(event))
                .isInstanceOf(InvalidLoanStateException.class)
                .hasMessageContaining("chưa qua bước thẩm định");
    }

    // ── 5. Từ chối từ PENDING_REVIEW vẫn hợp lệ ─────────────────────────────

    @Test
    void applyLoanReview_rejectFromPendingReview_succeeds() {
        LoanRequest loan = pendingReviewLoan("loan-5");
        when(loanRequestRepository.findById("loan-5")).thenReturn(Optional.of(loan));
        when(loanRequestRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        LoanReviewedEvent event = LoanReviewedEvent.builder()
                .loanId("loan-5").action("REJECT").rejectionReason("Thiếu chứng từ")
                .reviewedBy("admin").reviewedAt(LocalDateTime.now()).build();

        loanService.handleLoanReviewed(event);

        assertThat(loan.getStatus()).isEqualTo(LoanStatus.REJECTED);
    }

    // ── 6. totalDisbursed != loan.amount → reject trước payment call ─────────

    @Test
    void disburse_totalMismatch_throwsBeforeDebit() {
        LoanRequest loan = pendingApprovalLoan("loan-6", new BigDecimal("2.00"));
        loan.setStatus(LoanStatus.AWAITING_DISBURSEMENT);
        loan.setAmount(new BigDecimal("10000000"));
        when(loanRequestRepository.findById("loan-6")).thenReturn(Optional.of(loan));

        // Offer chỉ 9_000_000 thay vì 10_000_000
        LoanOffer offer = LoanOffer.builder()
                .id("offer-1").loanRequestId("loan-6")
                .investorId("inv-1").amount(new BigDecimal("9000000"))
                .status(OfferStatus.ACCEPTED).build();
        when(loanOfferRepository.findByLoanRequestIdAndStatus("loan-6", OfferStatus.ACCEPTED))
                .thenReturn(List.of(offer));

        assertThatThrownBy(() -> loanService.disburse("loan-6", "ops"))
                .isInstanceOf(InvalidLoanStateException.class)
                .hasMessageContaining("không khớp");

        // Không được gọi debit
        verify(paymentServiceClient, never()).debit(any(), any(), any(), any());
    }

    // ── 7. Khoản legacy (null fee) — giải ngân gross amount ──────────────────

    @Test
    void disburse_legacyLoan_creditGrossAmount() {
        LoanRequest loan = pendingReviewLoan("loan-7");
        loan.setStatus(LoanStatus.AWAITING_DISBURSEMENT);
        loan.setAmount(new BigDecimal("5000000"));
        // legacy: netDisbursement == null
        loan.setNetDisbursement(null);
        loan.setTotalFee(null);
        when(loanRequestRepository.findById("loan-7")).thenReturn(Optional.of(loan));
        when(loanRequestRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(loanRequestMapper.toResponse(any())).thenReturn(LoanResponse.builder().id("loan-7").build());

        LoanOffer offer = LoanOffer.builder()
                .id("offer-7").loanRequestId("loan-7")
                .investorId("inv-7").amount(new BigDecimal("5000000"))
                .status(OfferStatus.ACCEPTED).build();
        when(loanOfferRepository.findByLoanRequestIdAndStatus("loan-7", OfferStatus.ACCEPTED))
                .thenReturn(List.of(offer));

        loanService.disburse("loan-7", "ops");

        verify(paymentServiceClient).creditBorrower(eq("borrower-1"),
                eq(new BigDecimal("5000000")), anyString(), anyString());
    }

    // ── 8. Khoản mới — giải ngân netDisbursement ─────────────────────────────

    @Test
    void disburse_newLoan_creditNetAmount() {
        // amount = proposedAmount = 10_000_000, feeRate = 2% → net = 9_780_000
        LoanRequest loan = pendingApprovalLoan("loan-8", new BigDecimal("2.00"));
        loan.setStatus(LoanStatus.AWAITING_DISBURSEMENT);
        loan.setAmount(new BigDecimal("10000000"));
        when(loanRequestRepository.findById("loan-8")).thenReturn(Optional.of(loan));
        when(loanRequestRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(loanRequestMapper.toResponse(any())).thenReturn(LoanResponse.builder().id("loan-8").build());

        LoanOffer offer = LoanOffer.builder()
                .id("offer-8").loanRequestId("loan-8")
                .investorId("inv-8").amount(new BigDecimal("10000000"))
                .status(OfferStatus.ACCEPTED).build();
        when(loanOfferRepository.findByLoanRequestIdAndStatus("loan-8", OfferStatus.ACCEPTED))
                .thenReturn(List.of(offer));

        loanService.disburse("loan-8", "ops");

        verify(paymentServiceClient).creditBorrower(eq("borrower-1"),
                eq(new BigDecimal("9780000")), anyString(), anyString());
    }

    // ── 9. Snapshot netDisbursement lệch → reject ────────────────────────────

    @Test
    void disburse_snapshotMismatch_throwsError() {
        LoanRequest loan = pendingApprovalLoan("loan-9", new BigDecimal("2.00"));
        loan.setStatus(LoanStatus.AWAITING_DISBURSEMENT);
        loan.setAmount(new BigDecimal("10000000"));
        // Sửa snapshot thành giá trị sai
        loan.setNetDisbursement(new BigDecimal("9900000"));
        when(loanRequestRepository.findById("loan-9")).thenReturn(Optional.of(loan));

        LoanOffer offer = LoanOffer.builder()
                .id("offer-9").loanRequestId("loan-9")
                .investorId("inv-9").amount(new BigDecimal("10000000"))
                .status(OfferStatus.ACCEPTED).build();
        when(loanOfferRepository.findByLoanRequestIdAndStatus("loan-9", OfferStatus.ACCEPTED))
                .thenReturn(List.of(offer));

        assertThatThrownBy(() -> loanService.disburse("loan-9", "ops"))
                .isInstanceOf(InvalidLoanStateException.class)
                .hasMessageContaining("Snapshot netDisbursement");

        verify(paymentServiceClient, never()).debit(any(), any(), any(), any());
    }

    // ── 10. Làm tròn HALF_UP đúng đơn vị VND ─────────────────────────────────

    @Test
    void proposeLoan_roundingHalfUp_correctVnd() {
        // proposedAmount = 10_500_000, rate = 1.50%
        // appraisalFee = 10_500_000 * 1.5 / 100 = 157_500 (đúng chẵn)
        // vatAmount    = 157_500 * 0.10 = 15_750 (đúng chẵn)
        // totalFee     = 173_250
        // netDisburse  = 10_326_750
        LoanRequest loan = pendingReviewLoan("loan-10");
        stubMapper(loan);
        when(loanRequestRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        loanService.proposeLoan("loan-10", new BigDecimal("10500000"),
                new BigDecimal("12.00"), new BigDecimal("1.50"), null, "appraiser");

        assertThat(loan.getAppraisalFee()).isEqualByComparingTo(new BigDecimal("157500"));
        assertThat(loan.getVatAmount()).isEqualByComparingTo(new BigDecimal("15750"));
        assertThat(loan.getTotalFee()).isEqualByComparingTo(new BigDecimal("173250"));
        assertThat(loan.getNetDisbursement()).isEqualByComparingTo(new BigDecimal("10326750"));
    }
}
