package com.p2plending.loan.service;

import com.p2plending.loan.client.PaymentServiceClient;
import com.p2plending.loan.domain.entity.LoanRequest;
import com.p2plending.loan.domain.entity.RepaymentSchedule;
import com.p2plending.loan.domain.enums.LoanStatus;
import com.p2plending.loan.domain.enums.RepaymentStatus;
import com.p2plending.loan.domain.repository.LoanOfferRepository;
import com.p2plending.loan.domain.repository.LoanRequestRepository;
import com.p2plending.loan.domain.repository.PendingInvestorCreditRepository;
import com.p2plending.loan.domain.repository.RepaymentScheduleRepository;
import com.p2plending.loan.domain.repository.RepaymentTransactionRepository;
import com.p2plending.loan.dto.response.RepaymentMonitoringResponse;
import com.p2plending.loan.kafka.KafkaProducerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepaymentMonitoringTest {

    @Mock private RepaymentScheduleRepository scheduleRepository;
    @Mock private RepaymentTransactionRepository transactionRepository;
    @Mock private LoanRequestRepository loanRequestRepository;
    @Mock private LoanOfferRepository loanOfferRepository;
    @Mock private PendingInvestorCreditRepository pendingCreditRepository;
    @Mock private LoanProductService loanProductService;
    @Mock private RepaymentScheduleGenerator generator;
    @Mock private PaymentServiceClient paymentServiceClient;
    @Mock private KafkaProducerService kafkaProducerService;
    @Mock private CacheManager cacheManager;

    @InjectMocks private RepaymentService service;

    @Test
    void aggregatesOutstandingDebtAndPrioritizesOverdueInstallments() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        LoanRequest loan = LoanRequest.builder()
                .id("loan-1")
                .borrowerId("user-1")
                .status(LoanStatus.REPAYING)
                .isDeleted(false)
                .build();

        RepaymentSchedule overdue = RepaymentSchedule.builder()
                .loanId("loan-1")
                .periodNumber(1)
                .dueDate(today.minusDays(3))
                .principalDue(new BigDecimal("800"))
                .interestDue(new BigDecimal("200"))
                .totalDue(new BigDecimal("1000"))
                .paidAmount(new BigDecimal("100"))
                .interestPaid(new BigDecimal("100"))   // 100 đã trả áp vào lãi trước (Phí→Lãi→Gốc)
                .principalPaid(BigDecimal.ZERO)
                .lateFee(new BigDecimal("50"))
                .lateFeePaid(BigDecimal.ZERO)
                .principalPenalty(new BigDecimal("50")) // phí phạt gốc quá hạn 50
                .principalPenaltyPaid(BigDecimal.ZERO)
                .status(RepaymentStatus.OVERDUE)
                .build();
        RepaymentSchedule dueSoon = RepaymentSchedule.builder()
                .loanId("loan-1")
                .periodNumber(2)
                .dueDate(today.plusDays(3))
                .principalDue(new BigDecimal("500"))
                .interestDue(new BigDecimal("50"))
                .totalDue(new BigDecimal("550"))
                .paidAmount(BigDecimal.ZERO)
                .lateFee(BigDecimal.ZERO)
                .lateFeePaid(BigDecimal.ZERO)
                .status(RepaymentStatus.PENDING)
                .build();

        when(scheduleRepository.findByStatusNotAndIsDeletedFalseOrderByDueDateAscPeriodNumberAsc(
                RepaymentStatus.PAID)).thenReturn(List.of(overdue, dueSoon));
        when(loanRequestRepository.findAllById(any())).thenReturn(List.of(loan));

        RepaymentMonitoringResponse result = service.getMonitoring(7);

        assertThat(result.getOutstandingPrincipal()).isEqualByComparingTo("1300");
        assertThat(result.getOutstandingInterest()).isEqualByComparingTo("150");
        assertThat(result.getOutstandingLateFee()).isEqualByComparingTo("50");
        assertThat(result.getTotalOutstanding()).isEqualByComparingTo("1500");
        assertThat(result.getOverdueInstallments()).isEqualTo(1);
        assertThat(result.getDueSoonInstallments()).isEqualTo(1);
        assertThat(result.getOverdueCustomers()).isEqualTo(1);
        assertThat(result.getDueSoonCustomers()).isEqualTo(1);
        assertThat(result.getAttentionItems()).extracting("status")
                .containsExactly("OVERDUE", "DUE_SOON");
    }
}
