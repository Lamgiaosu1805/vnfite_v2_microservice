package com.p2plending.loan.service;

import com.p2plending.loan.domain.entity.LoanOffer;
import com.p2plending.loan.domain.entity.LoanRequest;
import com.p2plending.loan.domain.entity.RepaymentSchedule;
import com.p2plending.loan.domain.enums.LoanStatus;
import com.p2plending.loan.domain.enums.OfferStatus;
import com.p2plending.loan.domain.repository.LoanOfferRepository;
import com.p2plending.loan.domain.repository.LoanRequestRepository;
import com.p2plending.loan.domain.repository.RepaymentScheduleRepository;
import com.p2plending.loan.dto.response.CashflowResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Tổng hợp dữ liệu dòng tiền cho nhà đầu tư.
 * Tính từ LoanOffer (ACCEPTED) → RepaymentSchedule của từng khoản vay đang trả nợ.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CashflowService {

    private final LoanOfferRepository         loanOfferRepository;
    private final LoanRequestRepository       loanRequestRepository;
    private final RepaymentScheduleRepository repaymentScheduleRepository;

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final Set<LoanStatus> ACTIVE_STATUSES = Set.of(
            LoanStatus.ACTIVE, LoanStatus.FUNDED, LoanStatus.AWAITING_DISBURSEMENT,
            LoanStatus.DISBURSED, LoanStatus.REPAYING, LoanStatus.COMPLETED,
            LoanStatus.DEFAULTED, LoanStatus.AWAITING_BORROWER_APPROVAL
    );

    @Transactional(readOnly = true)
    public CashflowResponse getCashflow(String investorId) {
        // 1. Lấy tất cả offer ACCEPTED của nhà đầu tư
        List<LoanOffer> offers = loanOfferRepository.findByInvestorId(investorId)
                .stream()
                .filter(o -> o.getStatus() == OfferStatus.ACCEPTED && !o.isDeleted())
                .collect(Collectors.toList());

        if (offers.isEmpty()) {
            return buildEmpty();
        }

        // 2. Load toàn bộ khoản vay liên quan (1 query findAllById)
        Set<String> loanIds = offers.stream()
                .map(LoanOffer::getLoanRequestId)
                .collect(Collectors.toSet());

        Map<String, LoanRequest> loanMap = loanRequestRepository.findAllById(loanIds)
                .stream()
                .collect(Collectors.toMap(LoanRequest::getId, l -> l));

        // 3. Tổng hợp
        BigDecimal totalInvested         = BigDecimal.ZERO;
        BigDecimal totalReturnsExpected  = BigDecimal.ZERO;
        BigDecimal totalReturnsPaid      = BigDecimal.ZERO;

        List<CashflowResponse.UpcomingPayment> upcoming = new ArrayList<>();
        List<CashflowResponse.InvestmentItem>  history  = new ArrayList<>();

        // month → [expected, actual]
        TreeMap<String, BigDecimal[]> monthly = new TreeMap<>();

        for (LoanOffer offer : offers) {
            LoanRequest loan = loanMap.get(offer.getLoanRequestId());
            if (loan == null || loan.isDeleted()) continue;

            // Lịch sử đầu tư (tất cả offers, kể cả khoản chờ duyệt)
            history.add(CashflowResponse.InvestmentItem.builder()
                    .offerId(offer.getId())
                    .loanId(loan.getId())
                    .loanCode(loan.getLoanCode())
                    .borrowerId(loan.getBorrowerId())
                    .amount(offer.getAmount())
                    .loanStatus(loan.getStatus().name())
                    .interestRate(loan.getInterestRate())
                    .termMonths(loan.getTermMonths())
                    .investedAt(offer.getCreatedAt())
                    .build());

            // Chỉ tính tổng cho khoản có trạng thái active
            if (!ACTIVE_STATUSES.contains(loan.getStatus())) continue;

            totalInvested = totalInvested.add(offer.getAmount());

            // Lịch trả nợ chỉ có sau khi giải ngân: DISBURSED / REPAYING / COMPLETED
            if (loan.getStatus() != LoanStatus.DISBURSED
                    && loan.getStatus() != LoanStatus.REPAYING
                    && loan.getStatus() != LoanStatus.COMPLETED) continue;

            BigDecimal fundedAmount = loan.getFundedAmount();
            if (fundedAmount == null || fundedAmount.compareTo(BigDecimal.ZERO) == 0) {
                log.warn("Loan {} has zero fundedAmount, skipping cashflow calc", loan.getId());
                continue;
            }

            // Tỷ lệ nhà đầu tư trong khoản vay
            BigDecimal ratio = offer.getAmount().divide(fundedAmount, 10, RoundingMode.HALF_UP);

            List<RepaymentSchedule> schedule =
                    repaymentScheduleRepository.findByLoanIdAndIsDeletedFalseOrderByPeriodNumberAsc(loan.getId());

            for (RepaymentSchedule period : schedule) {
                BigDecimal investorShare = period.getTotalDue()
                        .multiply(ratio).setScale(0, RoundingMode.HALF_UP);
                BigDecimal investorPaid = period.getPaidAmount()
                        .multiply(ratio).setScale(0, RoundingMode.HALF_UP);

                totalReturnsExpected = totalReturnsExpected.add(investorShare);
                totalReturnsPaid     = totalReturnsPaid.add(investorPaid);

                // Cộng vào biểu đồ tháng
                String monthKey = period.getDueDate().format(MONTH_FMT);
                monthly.computeIfAbsent(monthKey, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                BigDecimal[] row = monthly.get(monthKey);
                row[0] = row[0].add(investorShare);
                row[1] = row[1].add(investorPaid);

                // Upcoming: chưa thanh toán + dueDate không quá 30 ngày đã qua
                if (!period.isSettled()
                        && !period.getDueDate().isBefore(LocalDate.now().minusDays(30))) {
                    upcoming.add(CashflowResponse.UpcomingPayment.builder()
                            .loanId(loan.getId())
                            .loanCode(loan.getLoanCode())
                            .dueDate(period.getDueDate())
                            .periodNumber(period.getPeriodNumber())
                            .investorShare(investorShare)
                            .status(period.getStatus().name())
                            .dpd(period.getDpd())
                            .build());
                }
            }
        }

        // Sắp xếp upcoming theo ngày đến hạn gần nhất
        upcoming.sort(Comparator.comparing(CashflowResponse.UpcomingPayment::getDueDate));

        // Sắp xếp history: mới nhất trước
        history.sort(Comparator.comparing(
                CashflowResponse.InvestmentItem::getInvestedAt,
                Comparator.nullsLast(Comparator.reverseOrder())
        ));

        // Biểu đồ tháng (TreeMap đã sorted)
        List<CashflowResponse.MonthlyChartItem> monthlyChart = monthly.entrySet().stream()
                .map(e -> CashflowResponse.MonthlyChartItem.builder()
                        .month(e.getKey())
                        .expected(e.getValue()[0])
                        .actual(e.getValue()[1])
                        .build())
                .collect(Collectors.toList());

        // Kỳ thanh toán gần nhất
        LocalDate  nextDate   = upcoming.isEmpty() ? null : upcoming.get(0).getDueDate();
        BigDecimal nextAmount = upcoming.isEmpty() ? null : upcoming.get(0).getInvestorShare();

        return CashflowResponse.builder()
                .summary(CashflowResponse.Summary.builder()
                        .totalInvested(totalInvested)
                        .totalReturnsExpected(totalReturnsExpected)
                        .totalReturnsPaid(totalReturnsPaid)
                        .nextPaymentDate(nextDate)
                        .nextPaymentAmount(nextAmount)
                        .build())
                .upcomingPayments(upcoming)
                .investmentHistory(history)
                .monthlyChart(monthlyChart)
                .build();
    }

    private CashflowResponse buildEmpty() {
        return CashflowResponse.builder()
                .summary(CashflowResponse.Summary.builder()
                        .totalInvested(BigDecimal.ZERO)
                        .totalReturnsExpected(BigDecimal.ZERO)
                        .totalReturnsPaid(BigDecimal.ZERO)
                        .nextPaymentDate(null)
                        .nextPaymentAmount(null)
                        .build())
                .upcomingPayments(Collections.emptyList())
                .investmentHistory(Collections.emptyList())
                .monthlyChart(Collections.emptyList())
                .build();
    }
}
