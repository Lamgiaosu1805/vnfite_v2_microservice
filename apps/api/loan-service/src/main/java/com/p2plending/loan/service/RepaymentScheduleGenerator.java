package com.p2plending.loan.service;

import com.p2plending.loan.domain.entity.RepaymentSchedule;
import com.p2plending.loan.domain.enums.RepaymentMethod;
import com.p2plending.loan.domain.enums.RepaymentStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Sinh lịch trả nợ từ điều khoản khoản vay. Thuần tính toán, không chạm DB.
 *
 * <h3>Kỳ đầu pro-rated — actual/365 cho mọi kỳ (repaymentDay = 5 hoặc 20)</h3>
 * <p>Khi người gọi vốn chọn ngày trả, mọi kỳ đều tính lãi theo <b>số ngày thực tế / 365</b>:
 * <pre>
 *   Lãi kỳ k = Dư_nợ_(k-1) × (annualRate/100) × (số_ngày_thực_kỳ_k / 365)
 * </pre>
 * <ul>
 *   <li>Kỳ 1: số ngày = giải ngân → ngày 5/20 tháng kế tiếp (có thể 27, 42 ngày…)</li>
 *   <li>Kỳ 2 trở đi: số ngày = ngày thực giữa 2 ngày đáo hạn liên tiếp (28–31 ngày)</li>
 *   <li>Gốc kỳ 1 = EMI − (P × r_m): phần gốc chuẩn của kỳ EMI đầu tiên, không đổi theo ngày</li>
 *   <li>Gốc kỳ 2→(n−1) = EMI − lãi_actual; Gốc kỳ n = dư_nợ còn lại (kỳ cuối)</li>
 * </ul>
 *
 * <h3>Fallback (repaymentDay = null)</h3>
 * <p>Nếu không có repaymentDay (dữ liệu cũ), lịch sinh theo kiểu cũ:
 * kỳ k đáo hạn = fundedDate + k tháng, lãi = dư_nợ × r_m (1%/tháng cố định).
 */
@Component
@Slf4j
public class RepaymentScheduleGenerator {

    private static final int        MONEY_SCALE  = 2;
    private static final BigDecimal DAYS_IN_YEAR = BigDecimal.valueOf(365);
    /** 100 × 365 — mẫu số duy nhất cho công thức lãi actual/365: B × r × days / 36500 */
    private static final BigDecimal RATE_BASE    = BigDecimal.valueOf(36500);

    /**
     * @param principal    số tiền vay
     * @param annualRate   lãi suất %/năm (vd 12.50)
     * @param termMonths   số kỳ EMI bình thường
     * @param method       kiểu trả nợ của sản phẩm
     * @param fundedDate   ngày khoản vay FUNDED
     * @param repaymentDay ngày trả nợ hàng tháng (5 hoặc 20); null = dùng logic cũ (+1 tháng)
     */
    public List<RepaymentSchedule> generate(BigDecimal principal, BigDecimal annualRate,
                                            int termMonths, RepaymentMethod method,
                                            LocalDate fundedDate, Integer repaymentDay) {
        if (repaymentDay != null && (repaymentDay == 5 || repaymentDay == 20)) {
            return generateWithRepaymentDay(principal, annualRate, termMonths, method, fundedDate, repaymentDay);
        }
        // Fallback: legacy logic (không có repaymentDay)
        BigDecimal monthlyRate = annualRate.divide(BigDecimal.valueOf(1200), 10, RoundingMode.HALF_UP);
        return switch (method) {
            case INTEREST_MONTHLY_PRINCIPAL_QUARTERLY ->
                    interestMonthlyPrincipalQuarterly(principal, monthlyRate, termMonths, fundedDate);
            case EMI_MONTHLY ->
                    equalInstallment(principal, monthlyRate, termMonths, fundedDate);
        };
    }

    /**
     * Sinh lịch có kỳ đầu pro-rated.
     *
     * <p><b>EMI_MONTHLY:</b>
     * <ul>
     *   <li>Kỳ 1: lãi pro-rated theo ngày thực tế + gốc bằng với phần gốc của kỳ EMI đầu tiên</li>
     *   <li>Kỳ 2 → termMonths: EMI đều bình thường trên số dư còn lại</li>
     * </ul>
     *
     * <p><b>INTEREST_MONTHLY_PRINCIPAL_QUARTERLY:</b>
     * <ul>
     *   <li>Kỳ 1: lãi pro-rated theo ngày thực tế, không trả gốc (gốc trả theo quý từ kỳ 3)</li>
     *   <li>Kỳ 2 → termMonths+1: lãi đều hàng tháng + gốc theo quý như bình thường</li>
     * </ul>
     */
    private List<RepaymentSchedule> generateWithRepaymentDay(BigDecimal principal, BigDecimal annualRate,
                                                             int termMonths, RepaymentMethod method,
                                                             LocalDate fundedDate, int repaymentDay) {
        LocalDate firstDueDate = firstDueDate(fundedDate, repaymentDay);
        long actualDays = ChronoUnit.DAYS.between(fundedDate, firstDueDate);
        BigDecimal monthlyRate = annualRate.divide(BigDecimal.valueOf(1200), 10, RoundingMode.HALF_UP);

        // Lãi kỳ 1 = P × annualRate × days / 36500  (actual/365, chia một lần duy nhất)
        BigDecimal firstInterest = calcInterest(principal, annualRate, actualDays);

        List<RepaymentSchedule> schedule = new ArrayList<>(termMonths + 1);

        if (method == RepaymentMethod.EMI_MONTHLY) {
            // Kỳ 1: lãi actual/365 pro-rated + gốc = phần gốc của EMI kỳ đầu tiên (EMI − P×r_m)
            BigDecimal emi = annuityPayment(principal, monthlyRate, termMonths);
            BigDecimal firstPrincipal = money(emi.subtract(money(principal.multiply(monthlyRate))));
            schedule.add(buildPeriod(1, firstDueDate, firstPrincipal, firstInterest));

            // Kỳ 2 → termMonths: lãi = dư_nợ × annualRate × actual_days/365 (actual/365 mỗi kỳ)
            BigDecimal remainingAfterFirst = principal.subtract(firstPrincipal);
            List<RepaymentSchedule> emiPeriods = equalInstallmentActualDays(
                    remainingAfterFirst, annualRate, termMonths - 1, firstDueDate);
            for (int i = 0; i < emiPeriods.size(); i++) {
                emiPeriods.get(i).setPeriodNumber(i + 2);
            }
            schedule.addAll(emiPeriods);

            log.debug("Generated actual/365 EMI schedule: kỳ 1 = {} ngày (gốc={}, lãi={}), kỳ 2-{} = actual/365 từ dư nợ {}",
                    actualDays, firstPrincipal, firstInterest, termMonths, remainingAfterFirst);
        } else {
            // INTEREST_MONTHLY_PRINCIPAL_QUARTERLY: kỳ 1 chỉ lãi pro-rated, gốc trả theo quý từ kỳ 3
            schedule.add(buildPeriod(1, firstDueDate, BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP), firstInterest));

            List<RepaymentSchedule> periods = interestMonthlyPrincipalQuarterly(principal, monthlyRate, termMonths, firstDueDate);
            for (int i = 0; i < periods.size(); i++) {
                periods.get(i).setPeriodNumber(i + 2);
            }
            schedule.addAll(periods);

            log.debug("Generated pro-rated QUARTERLY schedule: kỳ 1 = {} ngày lãi ({}), kỳ 2-{} = lãi tháng + gốc quý",
                    actualDays, firstInterest, termMonths + 1);
        }
        return schedule;
    }

    /**
     * Tính ngày đáo hạn kỳ đầu: luôn là ngày repaymentDay của tháng kế tiếp sau fundedDate,
     * bất kể fundedDate rơi vào ngày nào trong tháng.
     */
    private LocalDate firstDueDate(LocalDate fundedDate, int repaymentDay) {
        // Tháng kế tiếp, lấy ngày repaymentDay
        return fundedDate.plusMonths(1).withDayOfMonth(1).withDayOfMonth(
                Math.min(repaymentDay, fundedDate.plusMonths(1).lengthOfMonth())
        );
    }

    // ── Generators ───────────────────────────────────────────────────────────

    /**
     * Lãi theo số ngày thực tế giữa các ngày đáo hạn liên tiếp (actual/365).
     * Dùng cho kỳ 2..termMonths khi repaymentDay được chỉ định.
     *
     * <p>EMI nội bộ tính từ {@code principal} với lãi suất tháng chuẩn (annualRate/12).
     * Gốc = EMI − lãi_actual mỗi kỳ; kỳ cuối trả hết dư nợ còn lại.
     *
     * @param startDate ngày đáo hạn kỳ 1 (firstDueDate) — kỳ k của method này
     *                  tương ứng kỳ (k+1) tổng thể; dueDate = startDate.plusMonths(k)
     */
    private List<RepaymentSchedule> equalInstallmentActualDays(BigDecimal principal,
                                                               BigDecimal annualRate,
                                                               int n, LocalDate startDate) {
        BigDecimal monthlyRate = annualRate.divide(BigDecimal.valueOf(1200), 10, RoundingMode.HALF_UP);
        BigDecimal emi         = annuityPayment(principal, monthlyRate, n);

        List<RepaymentSchedule> schedule = new ArrayList<>(n);
        BigDecimal outstanding = principal;

        for (int k = 1; k <= n; k++) {
            LocalDate prevDate = startDate.plusMonths(k - 1);
            LocalDate dueDate  = startDate.plusMonths(k);
            long days          = ChronoUnit.DAYS.between(prevDate, dueDate);

            // Lãi = dư_nợ × annualRate × days / 36500  (công thức giống kỳ 1, chia một lần)
            BigDecimal interest = calcInterest(outstanding, annualRate, days);
            BigDecimal princ    = (k == n) ? outstanding : money(emi.subtract(interest));
            if (princ.compareTo(outstanding) > 0) princ = outstanding;
            outstanding = outstanding.subtract(princ);
            schedule.add(buildPeriod(k, dueDate, princ, interest));
        }
        return schedule;
    }

    /** Gốc + lãi đều mỗi kỳ (annuity, lãi = dư_nợ × monthlyRate). Dùng cho fallback. */
    private List<RepaymentSchedule> equalInstallment(BigDecimal principal, BigDecimal monthlyRate,
                                                     int n, LocalDate startDate) {
        BigDecimal emi = annuityPayment(principal, monthlyRate, n);
        List<RepaymentSchedule> schedule = new ArrayList<>(n);
        BigDecimal outstanding = principal;

        for (int k = 1; k <= n; k++) {
            BigDecimal interest = money(outstanding.multiply(monthlyRate));
            BigDecimal princ = (k == n) ? outstanding : money(emi.subtract(interest));
            if (princ.compareTo(outstanding) > 0) princ = outstanding;
            outstanding = outstanding.subtract(princ);
            schedule.add(buildPeriod(k, startDate.plusMonths(k), princ, interest));
        }
        return schedule;
    }

    /** Lãi đều hàng tháng, gốc chia đều theo quý. */
    private List<RepaymentSchedule> interestMonthlyPrincipalQuarterly(BigDecimal principal, BigDecimal monthlyRate,
                                                                      int n, LocalDate startDate) {
        int quarters = (int) Math.ceil(n / 3.0);
        BigDecimal principalPerQuarter = money(principal.divide(BigDecimal.valueOf(quarters), 10, RoundingMode.HALF_UP));
        List<RepaymentSchedule> schedule = new ArrayList<>(n);
        BigDecimal outstanding = principal;

        for (int k = 1; k <= n; k++) {
            BigDecimal interest = money(outstanding.multiply(monthlyRate));
            BigDecimal princ = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            if (k == n) {
                princ = outstanding;
            } else if (k % 3 == 0) {
                princ = principalPerQuarter.min(outstanding);
            }
            outstanding = outstanding.subtract(princ);
            schedule.add(buildPeriod(k, startDate.plusMonths(k), princ, interest));
        }
        return schedule;
    }

    /**
     * Công thức niên kim: EMI = P × r × (1+r)^n / ((1+r)^n − 1).
     * Dùng BigDecimal.pow(int) — chính xác hoàn toàn (không qua double).
     * r = 0 → P/n (trả gốc đều, không lãi).
     */
    private BigDecimal annuityPayment(BigDecimal principal, BigDecimal monthlyRate, int n) {
        if (monthlyRate.signum() == 0) {
            return money(principal.divide(BigDecimal.valueOf(n), 10, RoundingMode.HALF_UP));
        }
        // (1+r)^n: BigDecimal.pow(int) cho kết quả chính xác, scale = n × scale(monthlyRate)
        BigDecimal onePlusR = BigDecimal.ONE.add(monthlyRate);
        BigDecimal powN     = onePlusR.pow(n);
        // EMI = P × r × (1+r)^n / ((1+r)^n − 1)
        return money(
            principal.multiply(monthlyRate).multiply(powN)
                     .divide(powN.subtract(BigDecimal.ONE), 10, RoundingMode.HALF_UP)
        );
    }

    /**
     * Lãi actual/365: balance × annualRate(%) × days / 36500.
     * Chia một lần duy nhất (không có lần làm tròn trung gian).
     */
    private BigDecimal calcInterest(BigDecimal balance, BigDecimal annualRate, long days) {
        return money(
            balance.multiply(annualRate)
                   .multiply(BigDecimal.valueOf(days))
                   .divide(RATE_BASE, 10, RoundingMode.HALF_UP)
        );
    }

    private RepaymentSchedule buildPeriod(int period, LocalDate dueDate, BigDecimal princ, BigDecimal interest) {
        return RepaymentSchedule.builder()
                .periodNumber(period)
                .dueDate(dueDate)
                .principalDue(princ)
                .interestDue(interest)
                .totalDue(princ.add(interest))
                .paidAmount(BigDecimal.ZERO)
                .status(RepaymentStatus.PENDING)
                .dpd(0)
                .build();
    }

    private BigDecimal money(BigDecimal v) {
        return v.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
