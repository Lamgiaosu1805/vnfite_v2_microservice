package com.p2plending.loan.service;

import com.p2plending.loan.client.PaymentServiceClient;
import com.p2plending.loan.domain.entity.LoanRequest;
import com.p2plending.loan.domain.entity.RepaymentSchedule;
import com.p2plending.loan.domain.enums.LoanStatus;
import com.p2plending.loan.domain.enums.PaymentChannel;
import com.p2plending.loan.domain.enums.RepaymentStatus;
import com.p2plending.loan.domain.repository.*;
import com.p2plending.loan.dto.request.RecordPaymentRequest;
import com.p2plending.loan.dto.response.EarlySettlementQuoteResponse;
import com.p2plending.loan.dto.response.RepaymentScheduleResponse;
import com.p2plending.loan.kafka.KafkaProducerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Kiểm định công thức tính tiền lãi flat, thứ tự Phí→Lãi→Gốc, phí phạt quá hạn,
 * và tính tất toán sớm pro-rate.
 *
 * Kịch bản sử dụng: 100tr, 18%/năm, 12 kỳ, ngày 20 hàng tháng, giải ngân 10/01/2026.
 *   - Kỳ 1: 10/01 → 20/02 = 41 ngày; lãi = 100tr×18×41/36500 = 2,021,918; gốc = 8,333,333
 *   - Kỳ 2: 20/02 → 20/03 = 28 ngày; lãi = 100tr×18×28/36500 = 1,380,822; gốc = 8,333,333
 */
@ExtendWith(MockitoExtension.class)
class RepaymentAccountingTest {

    @Mock private RepaymentScheduleRepository    scheduleRepository;
    @Mock private RepaymentTransactionRepository transactionRepository;
    @Mock private LoanRequestRepository          loanRequestRepository;
    @Mock private LoanOfferRepository            loanOfferRepository;
    @Mock private PendingInvestorCreditRepository pendingCreditRepository;
    @Mock private InvestorDistributionLogRepository distributionLogRepository;
    @Mock private EarlySettlementRepository      earlySettlementRepository;
    @Mock private RepaymentAutoDebitAuditRepository autoDebitAuditRepository;
    @Mock private LoanProductService             loanProductService;
    @Mock private RepaymentScheduleGenerator     generator;
    @Mock private PaymentServiceClient           paymentServiceClient;
    @Mock private KafkaProducerService           kafkaProducerService;
    @Mock private CacheManager                   cacheManager;

    @InjectMocks private RepaymentService service;

    // Hằng số kịch bản
    private static final BigDecimal PRINCIPAL_100M   = new BigDecimal("100000000");
    private static final BigDecimal RATE_18          = new BigDecimal("18.00");
    // Kỳ 1: 41 ngày → lãi = 100_000_000 × 18 × 41 / 36500 = 2,021,918
    private static final BigDecimal INTEREST_P1      = new BigDecimal("2021918");
    private static final BigDecimal PRINCIPAL_PER_KY = new BigDecimal("8333333");
    // Kỳ 2: 28 ngày → lãi = 100_000_000 × 18 × 28 / 36500 = 1,380,822
    private static final BigDecimal INTEREST_P2      = new BigDecimal("1380822");

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(service, "defaultDpdThreshold", 30);
        ReflectionTestUtils.setField(service, "lateFeeEnabled", true);
        ReflectionTestUtils.setField(service, "tncnRate", new BigDecimal("0.05"));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private LoanRequest repayingLoan(String id) {
        return LoanRequest.builder()
                .id(id).loanSeq(1001L).status(LoanStatus.REPAYING)
                .amount(PRINCIPAL_100M).interestRate(RATE_18)
                .disbursedAt(LocalDateTime.of(2026, 1, 10, 0, 0))
                .isDeleted(false).build();
    }

    /** Kỳ quá hạn với phí phạt đã tích lũy. */
    private RepaymentSchedule overdueSchedule(String id, BigDecimal intPenalty, BigDecimal prinPenalty,
                                              BigDecimal intPaid, BigDecimal prinPaid) {
        return RepaymentSchedule.builder()
                .id(id).loanId("loan-1").periodNumber(1)
                .dueDate(LocalDate.of(2026, 2, 20))         // đã quá hạn (fixture chạy sau ngày này)
                .principalDue(PRINCIPAL_PER_KY)
                .interestDue(INTEREST_P1)
                .totalDue(PRINCIPAL_PER_KY.add(INTEREST_P1))
                .paidAmount(intPaid.add(prinPaid))
                .interestPaid(intPaid)
                .principalPaid(prinPaid)
                .interestPenalty(intPenalty)
                .interestPenaltyPaid(BigDecimal.ZERO)
                .principalPenalty(prinPenalty)
                .principalPenaltyPaid(BigDecimal.ZERO)
                .lateFee(intPenalty.add(prinPenalty))
                .lateFeePaid(BigDecimal.ZERO)
                .status(RepaymentStatus.OVERDUE).dpd(5)
                .isDeleted(false).build();
    }

    private RepaymentSchedule pendingSchedule(String id, int periodNumber, LocalDate dueDate,
                                              BigDecimal interest, BigDecimal principal) {
        return RepaymentSchedule.builder()
                .id(id).loanId("loan-1").periodNumber(periodNumber)
                .dueDate(dueDate)
                .principalDue(principal).interestDue(interest)
                .totalDue(principal.add(interest))
                .paidAmount(BigDecimal.ZERO).interestPaid(BigDecimal.ZERO).principalPaid(BigDecimal.ZERO)
                .interestPenalty(BigDecimal.ZERO).interestPenaltyPaid(BigDecimal.ZERO)
                .principalPenalty(BigDecimal.ZERO).principalPenaltyPaid(BigDecimal.ZERO)
                .lateFee(BigDecimal.ZERO).lateFeePaid(BigDecimal.ZERO)
                .status(RepaymentStatus.PENDING)
                .isDeleted(false).build();
    }

    private RecordPaymentRequest payment(BigDecimal amount) {
        RecordPaymentRequest r = new RecordPaymentRequest();
        r.setAmount(amount);
        r.setReason("test");
        r.setChannel(PaymentChannel.MANUAL_ADMIN);
        r.setRecordedBy("test-runner");
        return r;
    }

    private void stubRecordPayment(String loanId, LoanRequest loan, List<RepaymentSchedule> schedules) {
        when(loanRequestRepository.findById(loanId)).thenReturn(Optional.of(loan));
        when(scheduleRepository.findByLoanIdAndIsDeletedFalseOrderByPeriodNumberAsc(loanId))
                .thenReturn(schedules);
        when(transactionRepository.existsByScheduleIdAndChannelInAndIsDeletedFalse(any(), any()))
                .thenReturn(false);
        when(scheduleRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(loanRequestRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(cacheManager.getCache(any())).thenReturn(null);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 1. Thứ tự Phí → Lãi → Gốc — chỉ trả đủ phí phạt
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void partialPayment_onlyCoversLateFee_leavesInterestAndPrincipalUntouched() {
        LoanRequest loan = repayingLoan("loan-1");
        BigDecimal intPenalty  = new BigDecimal("2000");
        BigDecimal prinPenalty = new BigDecimal("30000");
        RepaymentSchedule s = overdueSchedule("s1", intPenalty, prinPenalty,
                BigDecimal.ZERO, BigDecimal.ZERO);

        stubRecordPayment("loan-1", loan, List.of(s));

        // Trả đúng bằng tổng phí phạt = 32,000
        BigDecimal payAmount = intPenalty.add(prinPenalty); // 32,000
        List<RepaymentScheduleResponse> result = service.recordPayment("loan-1", payment(payAmount));

        // Phí phạt phải xóa hết
        assertThat(s.getInterestPenaltyPaid()).isEqualByComparingTo(intPenalty);
        assertThat(s.getPrincipalPenaltyPaid()).isEqualByComparingTo(prinPenalty);
        assertThat(s.getLateFeePaid()).isEqualByComparingTo(payAmount);

        // Lãi + gốc vẫn chưa trả
        assertThat(s.getInterestPaid()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(s.getPrincipalPaid()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(s.getInterestOutstanding()).isEqualByComparingTo(INTEREST_P1);
        assertThat(s.getPrincipalOutstanding()).isEqualByComparingTo(PRINCIPAL_PER_KY);

        // Kỳ vẫn PARTIAL (chưa xong)
        assertThat(s.getStatus()).isEqualTo(RepaymentStatus.PARTIAL);
        assertThat(result).isNotEmpty();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 2. Trả đủ phí + lãi, thiếu gốc
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void partialPayment_coversFeePlusInterest_partialPrincipal() {
        LoanRequest loan = repayingLoan("loan-1");
        BigDecimal intPenalty  = new BigDecimal("1000");
        BigDecimal prinPenalty = new BigDecimal("18000");
        RepaymentSchedule s = overdueSchedule("s1", intPenalty, prinPenalty,
                BigDecimal.ZERO, BigDecimal.ZERO);

        stubRecordPayment("loan-1", loan, List.of(s));

        // Trả phí(19,000) + lãi(2,021,918) + 500,000 gốc = 2,540,918
        BigDecimal partialPrin = new BigDecimal("500000");
        BigDecimal payAmount = intPenalty.add(prinPenalty).add(INTEREST_P1).add(partialPrin);
        service.recordPayment("loan-1", payment(payAmount));

        // Phí phạt: xóa hết
        assertThat(s.getLateFeePaid()).isEqualByComparingTo(intPenalty.add(prinPenalty));
        assertThat(s.getLateFeeOutstanding()).isEqualByComparingTo(BigDecimal.ZERO);

        // Lãi: trả đủ
        assertThat(s.getInterestPaid()).isEqualByComparingTo(INTEREST_P1);
        assertThat(s.getInterestOutstanding()).isEqualByComparingTo(BigDecimal.ZERO);

        // Gốc: trả được 500,000 / 8,333,333
        assertThat(s.getPrincipalPaid()).isEqualByComparingTo(partialPrin);
        assertThat(s.getPrincipalOutstanding())
                .isEqualByComparingTo(PRINCIPAL_PER_KY.subtract(partialPrin));

        // Kỳ vẫn PARTIAL
        assertThat(s.getStatus()).isEqualTo(RepaymentStatus.PARTIAL);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 3. Trả đủ toàn bộ kỳ quá hạn → PAID
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void fullPayment_overdueSchedule_becomesStatusPaid() {
        LoanRequest loan = repayingLoan("loan-1");
        BigDecimal intPenalty  = new BigDecimal("554");
        BigDecimal prinPenalty = new BigDecimal("6164");
        RepaymentSchedule s = overdueSchedule("s1", intPenalty, prinPenalty,
                BigDecimal.ZERO, BigDecimal.ZERO);

        stubRecordPayment("loan-1", loan, List.of(s));

        // Tổng thanh toán = phí + lãi + gốc
        BigDecimal totalFee = intPenalty.add(prinPenalty);
        BigDecimal total = totalFee.add(INTEREST_P1).add(PRINCIPAL_PER_KY);
        service.recordPayment("loan-1", payment(total));

        assertThat(s.getStatus()).isEqualTo(RepaymentStatus.PAID);
        assertThat(s.getLateFeePaid()).isEqualByComparingTo(totalFee);
        assertThat(s.getInterestPaid()).isEqualByComparingTo(INTEREST_P1);
        assertThat(s.getPrincipalPaid()).isEqualByComparingTo(PRINCIPAL_PER_KY);
        assertThat(s.getPaidAmount()).isEqualByComparingTo(INTEREST_P1.add(PRINCIPAL_PER_KY));
        assertThat(s.getTotalOutstanding()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 4. Tiền vượt kỳ 1 → áp sang kỳ 2
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void payment_exceedsFirstPeriod_overflowAppliedToSecondPeriod() {
        LoanRequest loan = repayingLoan("loan-1");
        RepaymentSchedule s1 = pendingSchedule("s1", 1,
                LocalDate.of(2026, 2, 20), INTEREST_P1, PRINCIPAL_PER_KY);
        RepaymentSchedule s2 = pendingSchedule("s2", 2,
                LocalDate.of(2026, 3, 20), INTEREST_P2, PRINCIPAL_PER_KY);

        stubRecordPayment("loan-1", loan, List.of(s1, s2));

        // Trả kỳ 1 đầy đủ + một phần lãi kỳ 2
        BigDecimal totalP1 = PRINCIPAL_PER_KY.add(INTEREST_P1);
        BigDecimal partialInterestP2 = new BigDecimal("500000");
        service.recordPayment("loan-1", payment(totalP1.add(partialInterestP2)));

        // Kỳ 1 → PAID
        assertThat(s1.getStatus()).isEqualTo(RepaymentStatus.PAID);
        assertThat(s1.getInterestPaid()).isEqualByComparingTo(INTEREST_P1);
        assertThat(s1.getPrincipalPaid()).isEqualByComparingTo(PRINCIPAL_PER_KY);

        // Kỳ 2 → nhận phần dư vào lãi trước
        assertThat(s2.getInterestPaid()).isEqualByComparingTo(partialInterestP2);
        assertThat(s2.getPrincipalPaid()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(s2.getStatus()).isEqualTo(RepaymentStatus.PARTIAL);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 5. DPD sweep: phí phạt tích lũy trên số dư còn lại sau partial
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void dpdSweep_afterPartialPayment_penaltyAccumulatesOnRemainingBalance() {
        LoanRequest loan = repayingLoan("loan-1");
        // Kỳ đã quá hạn: lãi đã trả hết, gốc còn lại 7,000,000 (đã trả 1,333,333)
        BigDecimal principalPaid = new BigDecimal("1333333");
        BigDecimal principalRemaining = PRINCIPAL_PER_KY.subtract(principalPaid); // 7,000,000
        RepaymentSchedule s = RepaymentSchedule.builder()
                .id("s1").loanId("loan-1").periodNumber(1)
                .dueDate(LocalDate.now().minusDays(2))   // quá hạn 2 ngày (dpd=0 trước sweep)
                .principalDue(PRINCIPAL_PER_KY).interestDue(INTEREST_P1)
                .totalDue(PRINCIPAL_PER_KY.add(INTEREST_P1))
                .paidAmount(INTEREST_P1.add(principalPaid))
                .interestPaid(INTEREST_P1)         // lãi đã trả xong
                .principalPaid(principalPaid)
                .interestPenalty(BigDecimal.ZERO).interestPenaltyPaid(BigDecimal.ZERO)
                .principalPenalty(BigDecimal.ZERO).principalPenaltyPaid(BigDecimal.ZERO)
                .lateFee(BigDecimal.ZERO).lateFeePaid(BigDecimal.ZERO)
                .dpd(0).status(RepaymentStatus.PARTIAL)
                .isDeleted(false).build();

        when(loanRequestRepository.findByStatusInAndIsDeletedFalse(any())).thenReturn(List.of(loan));
        when(scheduleRepository.findByLoanIdAndIsDeletedFalseOrderByPeriodNumberAsc(loan.getId()))
                .thenReturn(List.of(s));
        when(scheduleRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(loanRequestRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.runDpdSweep();

        // Phí phạt lãi = 0 (lãi = 0, interestOutstanding = 0)
        assertThat(s.getInterestPenalty()).isEqualByComparingTo(BigDecimal.ZERO);

        // Phí phạt gốc = principalRemaining × 18% × 150% × 2 ngày / 365
        // = 7,000,000 × 18 × 150% × 2 / 36500 = 7,000,000 × 0.18 × 1.5 × 2 / 365
        // = 7,000,000 × 0.54 / 365 × 2 = 3,780,000 / 365 × 2 ≈ 20,712 (2 ngày tích lũy)
        BigDecimal expectedPrinPenalty = principalRemaining
                .multiply(RATE_18).multiply(new BigDecimal("1.5")).multiply(BigDecimal.valueOf(2))
                .divide(new BigDecimal("36500"), 0, java.math.RoundingMode.HALF_UP);
        // Cho phép sai lệch ±2 VND do làm tròn delta mỗi ngày
        assertThat(s.getPrincipalPenalty())
                .isGreaterThan(expectedPrinPenalty.subtract(new BigDecimal("2")))
                .isLessThan(expectedPrinPenalty.add(new BigDecimal("2")));

        // DPD đã cập nhật
        assertThat(s.getDpd()).isEqualTo(2);
        assertThat(s.getStatus()).isEqualTo(RepaymentStatus.OVERDUE);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 6. DPD sweep: khi lãi chưa trả, phí phạt lãi tính trên interestOutstanding
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void dpdSweep_interestUnpaid_interestPenaltyAccumulates() {
        LoanRequest loan = repayingLoan("loan-1");
        RepaymentSchedule s = RepaymentSchedule.builder()
                .id("s1").loanId("loan-1").periodNumber(1)
                .dueDate(LocalDate.now().minusDays(1))
                .principalDue(PRINCIPAL_PER_KY).interestDue(INTEREST_P1)
                .totalDue(PRINCIPAL_PER_KY.add(INTEREST_P1))
                .paidAmount(BigDecimal.ZERO).interestPaid(BigDecimal.ZERO).principalPaid(BigDecimal.ZERO)
                .interestPenalty(BigDecimal.ZERO).interestPenaltyPaid(BigDecimal.ZERO)
                .principalPenalty(BigDecimal.ZERO).principalPenaltyPaid(BigDecimal.ZERO)
                .lateFee(BigDecimal.ZERO).lateFeePaid(BigDecimal.ZERO)
                .dpd(0).status(RepaymentStatus.PENDING)
                .isDeleted(false).build();

        when(loanRequestRepository.findByStatusInAndIsDeletedFalse(any())).thenReturn(List.of(loan));
        when(scheduleRepository.findByLoanIdAndIsDeletedFalseOrderByPeriodNumberAsc(loan.getId()))
                .thenReturn(List.of(s));
        when(scheduleRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(loanRequestRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.runDpdSweep();

        // Phí phạt lãi DPD 1 = 2,021,918 × 10% × 1 / 365 = 554
        assertThat(s.getInterestPenalty()).isEqualByComparingTo("554");

        // Phí phạt gốc DPD 1 = 8,333,333 × 18% × 150% × 1 / 365 = 6,164 (sau làm tròn)
        // = 8,333,333 × 0.27 / 365 = 2,250,000 / 365 ≈ 6,164
        assertThat(s.getPrincipalPenalty())
                .isGreaterThanOrEqualTo(new BigDecimal("6163"))
                .isLessThanOrEqualTo(new BigDecimal("6165"));

        assertThat(s.getDpd()).isEqualTo(1);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 7. Tất toán sớm: kỳ đang chạy tính lãi pro-rate, kỳ tương lai miễn
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void earlySettlementQuote_currentPeriodProRate_futurePeriodsFree() {
        LoanRequest loan = repayingLoan("loan-1");
        // Mới dùng vốn 12 ngày trong kỳ hạn ~97 ngày → < 2/3 → phí tất toán ÁP DỤNG.
        loan.setDisbursedAt(LocalDateTime.now().minusDays(12));

        // Dùng ngày tương đối để test tất định (không phụ thuộc ngày chạy fixture).
        LocalDate due1 = LocalDate.now().minusDays(5);   // kỳ 1 đã trả, là mốc bắt đầu kỳ 2
        LocalDate due2 = LocalDate.now().plusDays(25);    // kỳ 2 đang chạy (start=due1 < today)
        LocalDate due3 = LocalDate.now().plusDays(55);    // hoàn toàn tương lai
        LocalDate due4 = LocalDate.now().plusDays(85);    // hoàn toàn tương lai

        // Kỳ 1: đã PAID
        RepaymentSchedule paid = pendingSchedule("s1", 1, due1, INTEREST_P1, PRINCIPAL_PER_KY);
        paid.setStatus(RepaymentStatus.PAID);
        paid.setInterestPaid(INTEREST_P1);
        paid.setPrincipalPaid(PRINCIPAL_PER_KY);
        paid.setPaidAmount(INTEREST_P1.add(PRINCIPAL_PER_KY));

        RepaymentSchedule s2 = pendingSchedule("s2", 2, due2, INTEREST_P2, PRINCIPAL_PER_KY);
        RepaymentSchedule s3 = pendingSchedule("s3", 3, due3,
                new BigDecimal("1528767"), PRINCIPAL_PER_KY);
        RepaymentSchedule s4 = pendingSchedule("s4", 4, due4,
                new BigDecimal("1528767"), new BigDecimal("8333337")); // kỳ cuối

        when(loanRequestRepository.findById("loan-1")).thenReturn(Optional.of(loan));
        when(scheduleRepository.findByLoanIdAndIsDeletedFalseOrderByPeriodNumberAsc("loan-1"))
                .thenReturn(List.of(paid, s2, s3, s4));
        when(earlySettlementRepository.existsByLoanIdAndIsDeletedFalse("loan-1")).thenReturn(false);

        EarlySettlementQuoteResponse quote = service.quoteEarlySettlement("loan-1");

        // Gốc còn lại = 3 kỳ × 8,333,333 + kỳ 4 = 8,333,333 × 2 + 8,333,337
        BigDecimal expectedPrincipal = PRINCIPAL_PER_KY.multiply(BigDecimal.valueOf(2))
                .add(new BigDecimal("8333337"));
        assertThat(quote.remainingPrincipal()).isEqualByComparingTo(expectedPrincipal);

        // Kỳ 2 đang chạy: có lãi pro-rate > 0
        assertThat(quote.interestToDate()).isGreaterThan(BigDecimal.ZERO);
        // Lãi pro-rate kỳ 2 ≤ interest_due của kỳ 2 (không vượt mức lãi của kỳ)
        assertThat(quote.interestToDate()).isLessThanOrEqualTo(INTEREST_P2);

        // Phí phạt = 0 (không quá hạn)
        assertThat(quote.penaltyOutstanding()).isEqualByComparingTo(BigDecimal.ZERO);

        // Phí tất toán = 5% × gốc còn lại (đã dùng < 2/3 kỳ hạn, và 5% > mức sàn 500k)
        BigDecimal expectedFee = expectedPrincipal.multiply(new BigDecimal("5"))
                .divide(new BigDecimal("100"), 0, java.math.RoundingMode.HALF_UP);
        assertThat(quote.settlementFee()).isEqualByComparingTo(expectedFee);

        // Total payoff = gốc + lãi pro-rate + phí
        assertThat(quote.totalPayoff())
                .isEqualByComparingTo(expectedPrincipal.add(quote.interestToDate()).add(expectedFee));

        assertThat(quote.settled()).isFalse();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 8. Tất toán sớm: kỳ đã quá hạn → tính đủ lãi + phí phạt
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void earlySettlementQuote_overdueCurrentPeriod_fullInterestAndPenalty() {
        LoanRequest loan = repayingLoan("loan-1");
        // Khoản chỉ 1 kỳ, đã quá hạn → đã dùng vốn vượt kỳ hạn (≥ 2/3) → MIỄN phí tất toán.
        loan.setDisbursedAt(LocalDateTime.now().minusDays(35));
        BigDecimal intPenalty  = new BigDecimal("2000");
        BigDecimal prinPenalty = new BigDecimal("30000");

        // Kỳ 1 quá hạn (dueDate trong quá khứ), chưa trả gì
        RepaymentSchedule overdue = RepaymentSchedule.builder()
                .id("s1").loanId("loan-1").periodNumber(1)
                .dueDate(LocalDate.now().minusDays(5))   // quá hạn 5 ngày
                .principalDue(PRINCIPAL_PER_KY).interestDue(INTEREST_P1)
                .totalDue(PRINCIPAL_PER_KY.add(INTEREST_P1))
                .paidAmount(BigDecimal.ZERO).interestPaid(BigDecimal.ZERO).principalPaid(BigDecimal.ZERO)
                .interestPenalty(intPenalty).interestPenaltyPaid(BigDecimal.ZERO)
                .principalPenalty(prinPenalty).principalPenaltyPaid(BigDecimal.ZERO)
                .lateFee(intPenalty.add(prinPenalty)).lateFeePaid(BigDecimal.ZERO)
                .dpd(5).status(RepaymentStatus.OVERDUE)
                .isDeleted(false).build();

        when(loanRequestRepository.findById("loan-1")).thenReturn(Optional.of(loan));
        when(scheduleRepository.findByLoanIdAndIsDeletedFalseOrderByPeriodNumberAsc("loan-1"))
                .thenReturn(List.of(overdue));
        when(earlySettlementRepository.existsByLoanIdAndIsDeletedFalse("loan-1")).thenReturn(false);

        EarlySettlementQuoteResponse quote = service.quoteEarlySettlement("loan-1");

        // Lãi phải tính đủ (kỳ đã đến hạn)
        assertThat(quote.interestToDate()).isEqualByComparingTo(INTEREST_P1);

        // Phí phạt phải hiển thị đủ
        assertThat(quote.penaltyOutstanding()).isEqualByComparingTo(intPenalty.add(prinPenalty));

        // Gốc = toàn bộ kỳ 1
        assertThat(quote.remainingPrincipal()).isEqualByComparingTo(PRINCIPAL_PER_KY);

        // Phí tất toán = 0 (đã dùng vốn vượt kỳ hạn → miễn phí theo hợp đồng)
        assertThat(quote.settlementFee()).isEqualByComparingTo(BigDecimal.ZERO);
        // Total = gốc + lãi đủ + phí phạt (không có phí tất toán)
        assertThat(quote.totalPayoff())
                .isEqualByComparingTo(PRINCIPAL_PER_KY
                        .add(INTEREST_P1)
                        .add(intPenalty.add(prinPenalty)));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 11. Tất toán sớm: đã dùng vốn ≥ 2/3 kỳ hạn → MIỄN phí (theo hợp đồng)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void earlySettlementQuote_usedTwoThirdsOfTerm_feeWaived() {
        LoanRequest loan = repayingLoan("loan-1");
        // Đã dùng vốn 80 ngày, kỳ hạn còn tới hạn cuối +20 ngày → tổng ~100 ngày, tỷ lệ 0.8 ≥ 2/3.
        loan.setDisbursedAt(LocalDateTime.now().minusDays(80));

        LocalDate due1 = LocalDate.now().minusDays(5);   // kỳ 1 đã trả (mốc bắt đầu kỳ 2)
        LocalDate due2 = LocalDate.now().plusDays(20);    // kỳ cuối, còn trong tương lai gần

        RepaymentSchedule paid = pendingSchedule("s1", 1, due1, INTEREST_P1, PRINCIPAL_PER_KY);
        paid.setStatus(RepaymentStatus.PAID);
        paid.setInterestPaid(INTEREST_P1);
        paid.setPrincipalPaid(PRINCIPAL_PER_KY);
        paid.setPaidAmount(INTEREST_P1.add(PRINCIPAL_PER_KY));

        RepaymentSchedule s2 = pendingSchedule("s2", 2, due2, INTEREST_P2, PRINCIPAL_PER_KY);

        when(loanRequestRepository.findById("loan-1")).thenReturn(Optional.of(loan));
        when(scheduleRepository.findByLoanIdAndIsDeletedFalseOrderByPeriodNumberAsc("loan-1"))
                .thenReturn(List.of(paid, s2));
        when(earlySettlementRepository.existsByLoanIdAndIsDeletedFalse("loan-1")).thenReturn(false);

        EarlySettlementQuoteResponse quote = service.quoteEarlySettlement("loan-1");

        // Còn gốc kỳ 2 để tất toán, nhưng phí = 0 vì đã dùng ≥ 2/3 kỳ hạn
        assertThat(quote.remainingPrincipal()).isEqualByComparingTo(PRINCIPAL_PER_KY);
        assertThat(quote.settlementFee()).isEqualByComparingTo(BigDecimal.ZERO);
        // Tổng payoff không gồm phí tất toán
        assertThat(quote.totalPayoff())
                .isEqualByComparingTo(PRINCIPAL_PER_KY.add(quote.interestToDate()));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 12. Tất toán sớm: 5% × gốc còn lại < 500k → áp mức sàn 500.000đ
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void earlySettlementQuote_feeBelowFloor_appliesMinimumFee() {
        LoanRequest loan = repayingLoan("loan-1");
        loan.setDisbursedAt(LocalDateTime.now().minusDays(10));  // mới dùng vốn → < 2/3

        // Gốc còn lại nhỏ: 5,000,000 → 5% = 250,000 < 500,000 → phải nâng lên mức sàn 500k
        BigDecimal smallPrincipal = new BigDecimal("5000000");
        LocalDate due1 = LocalDate.now().plusDays(40);   // kỳ tương lai
        LocalDate due2 = LocalDate.now().plusDays(70);

        RepaymentSchedule s1 = pendingSchedule("s1", 1, due1,
                new BigDecimal("100000"), new BigDecimal("2500000"));
        RepaymentSchedule s2 = pendingSchedule("s2", 2, due2,
                new BigDecimal("100000"), new BigDecimal("2500000"));

        when(loanRequestRepository.findById("loan-1")).thenReturn(Optional.of(loan));
        when(scheduleRepository.findByLoanIdAndIsDeletedFalseOrderByPeriodNumberAsc("loan-1"))
                .thenReturn(List.of(s1, s2));
        when(earlySettlementRepository.existsByLoanIdAndIsDeletedFalse("loan-1")).thenReturn(false);

        EarlySettlementQuoteResponse quote = service.quoteEarlySettlement("loan-1");

        assertThat(quote.remainingPrincipal()).isEqualByComparingTo(smallPrincipal);
        // 5% × 5,000,000 = 250,000 < 500,000 → áp mức sàn
        assertThat(quote.settlementFee()).isEqualByComparingTo(new BigDecimal("500000"));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 9. Bất biến tổng: paidAmount = interestPaid + principalPaid luôn đúng
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void partialPayment_invariant_paidAmountEqualsSumOfParts() {
        LoanRequest loan = repayingLoan("loan-1");
        BigDecimal intPenalty  = new BigDecimal("5000");
        BigDecimal prinPenalty = new BigDecimal("25000");
        RepaymentSchedule s = overdueSchedule("s1", intPenalty, prinPenalty,
                BigDecimal.ZERO, BigDecimal.ZERO);

        stubRecordPayment("loan-1", loan, List.of(s));

        // Trả nhiều hơn phí nhưng ít hơn phí+lãi+gốc
        BigDecimal payAmount = new BigDecimal("1000000");
        service.recordPayment("loan-1", payment(payAmount));

        // Bất biến: paidAmount = interestPaid + principalPaid
        assertThat(s.getPaidAmount())
                .isEqualByComparingTo(s.getInterestPaid().add(s.getPrincipalPaid()));

        // Bất biến: lateFeePaid = interestPenaltyPaid + principalPenaltyPaid
        assertThat(s.getLateFeePaid())
                .isEqualByComparingTo(s.getInterestPenaltyPaid().add(s.getPrincipalPenaltyPaid()));

        // Tổng paid = lateFeePaid + paidAmount ≤ payAmount (không trả quá số tiền vào)
        BigDecimal totalApplied = s.getLateFeePaid().add(s.getPaidAmount());
        assertThat(totalApplied).isLessThanOrEqualTo(payAmount);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 10. Thứ tự ưu tiên: phí phạt lãi trả TRƯỚC phí phạt gốc
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void partialPayment_interestPenaltyPaidBeforePrincipalPenalty() {
        LoanRequest loan = repayingLoan("loan-1");
        BigDecimal intPenalty  = new BigDecimal("10000");
        BigDecimal prinPenalty = new BigDecimal("50000");
        RepaymentSchedule s = overdueSchedule("s1", intPenalty, prinPenalty,
                BigDecimal.ZERO, BigDecimal.ZERO);

        stubRecordPayment("loan-1", loan, List.of(s));

        // Trả chỉ đủ phí phạt lãi + một phần phí phạt gốc (15,000 < 60,000 tổng phí)
        service.recordPayment("loan-1", payment(new BigDecimal("15000")));

        // Phí phạt lãi phải xóa hết trước
        assertThat(s.getInterestPenaltyPaid()).isEqualByComparingTo(intPenalty);
        // Phần còn lại (5,000) đi vào phí phạt gốc
        assertThat(s.getPrincipalPenaltyPaid()).isEqualByComparingTo(new BigDecimal("5000"));
        // Lãi chưa được chạm tới
        assertThat(s.getInterestPaid()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(s.getPrincipalPaid()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
