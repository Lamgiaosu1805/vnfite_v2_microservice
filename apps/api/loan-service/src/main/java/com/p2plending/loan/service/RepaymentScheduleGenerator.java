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
 * <h3>Mô hình tính tiền (chốt với ban lãnh đạo)</h3>
 * <ul>
 *   <li><b>Lãi flat trên gốc giải ngân ban đầu</b> — lãi mỗi kỳ KHÔNG giảm khi trả bớt gốc:
 *       <pre>Lãi kỳ k = Gốc_ban_đầu × (annualRate/100) × (số_ngày_thực_kỳ_k / 365)</pre></li>
 *   <li><b>Gốc chia đều</b> mỗi kỳ (gốc/n); kỳ cuối gánh phần dư làm tròn.</li>
 *   <li><b>Số ngày thực tế (actual/365)</b>:
 *     <ul>
 *       <li>Kỳ 1: từ ngày giải ngân → ngày 5/20 tháng kế tiếp (có thể 27, 42 ngày…)</li>
 *       <li>Kỳ 2→n: số ngày thực giữa 2 ngày đáo hạn liên tiếp (28–31 ngày)</li>
 *     </ul>
 *     Lãi các kỳ đầy đủ chênh nhẹ theo độ dài tháng — đúng tinh thần actual/365.</li>
 * </ul>
 *
 * <p>repaymentDay = 5 hoặc 20: kỳ 1 pro-rated tới ngày đó. repaymentDay = null (dữ liệu cũ):
 * ngày đáo hạn = fundedDate + k tháng (mốc kỷ niệm), vẫn dùng cùng công thức lãi flat actual/365.
 */
@Component
@Slf4j
public class RepaymentScheduleGenerator {

    private static final int        MONEY_SCALE = 0;
    /** 100 × 365 — mẫu số cho công thức lãi actual/365: gốc × rate(%) × days / 36500 */
    private static final BigDecimal RATE_BASE   = BigDecimal.valueOf(36500);

    /**
     * @param principal    số tiền vay (gốc giải ngân ban đầu) — cơ sở tính lãi flat mọi kỳ
     * @param annualRate   lãi suất %/năm (vd 18.00)
     * @param termMonths   số kỳ
     * @param method       kiểu trả nợ của sản phẩm
     * @param fundedDate   ngày khoản vay FUNDED (mốc bắt đầu kỳ 1)
     * @param repaymentDay ngày trả nợ hàng tháng (5 hoặc 20); null = mốc +k tháng từ fundedDate
     */
    public List<RepaymentSchedule> generate(BigDecimal principal, BigDecimal annualRate,
                                            int termMonths, RepaymentMethod method,
                                            LocalDate fundedDate, Integer repaymentDay) {
        List<LocalDate> dueDates = computeDueDates(fundedDate, termMonths, repaymentDay);
        return switch (method) {
            case INTEREST_MONTHLY_PRINCIPAL_QUARTERLY ->
                    flatPrincipalQuarterly(principal, annualRate, termMonths, fundedDate, dueDates);
            case EMI_MONTHLY ->
                    flatEqualPrincipal(principal, annualRate, termMonths, fundedDate, dueDates);
        };
    }

    // ── Ngày đáo hạn ──────────────────────────────────────────────────────────

    /** Danh sách ngày đáo hạn của termMonths kỳ. */
    private List<LocalDate> computeDueDates(LocalDate fundedDate, int termMonths, Integer repaymentDay) {
        List<LocalDate> dueDates = new ArrayList<>(termMonths);
        if (repaymentDay != null && (repaymentDay == 5 || repaymentDay == 20)) {
            LocalDate first = firstDueDate(fundedDate, repaymentDay);
            for (int k = 0; k < termMonths; k++) dueDates.add(first.plusMonths(k));
        } else {
            for (int k = 0; k < termMonths; k++) dueDates.add(fundedDate.plusMonths(k + 1L));
        }
        return dueDates;
    }

    /**
     * Ngày đáo hạn kỳ đầu = ngày repaymentDay của THÁNG KẾ TIẾP sau fundedDate,
     * bất kể fundedDate rơi vào ngày nào (clamp theo số ngày của tháng kế tiếp).
     */
    private LocalDate firstDueDate(LocalDate fundedDate, int repaymentDay) {
        LocalDate nextMonth = fundedDate.plusMonths(1);
        return nextMonth.withDayOfMonth(Math.min(repaymentDay, nextMonth.lengthOfMonth()));
    }

    // ── Sinh lịch ─────────────────────────────────────────────────────────────

    /**
     * EMI_MONTHLY (mặc định cá nhân): gốc chia ĐỀU mỗi kỳ, lãi flat actual/365 trên gốc ban đầu.
     * Kỳ cuối gánh phần dư làm tròn của gốc để tổng gốc khớp tuyệt đối số vay.
     */
    private List<RepaymentSchedule> flatEqualPrincipal(BigDecimal principal, BigDecimal annualRate,
                                                       int termMonths, LocalDate fundedDate,
                                                       List<LocalDate> dueDates) {
        BigDecimal basePrincipal = money(principal.divide(BigDecimal.valueOf(termMonths), 10, RoundingMode.HALF_UP));
        List<RepaymentSchedule> schedule = new ArrayList<>(termMonths);

        for (int k = 1; k <= termMonths; k++) {
            LocalDate start   = (k == 1) ? fundedDate : dueDates.get(k - 2);
            LocalDate dueDate = dueDates.get(k - 1);
            long days         = ChronoUnit.DAYS.between(start, dueDate);

            BigDecimal interest = calcInterest(principal, annualRate, days);
            BigDecimal princ    = (k == termMonths)
                    ? principal.subtract(basePrincipal.multiply(BigDecimal.valueOf(termMonths - 1L)))
                    : basePrincipal;
            schedule.add(buildPeriod(k, dueDate, money(princ), interest));
        }
        log.debug("Generated FLAT EMI schedule: {} kỳ, gốc đều {}/kỳ, lãi flat actual/365 trên gốc {}",
                termMonths, basePrincipal, principal);
        return schedule;
    }

    /**
     * INTEREST_MONTHLY_PRINCIPAL_QUARTERLY (hộ KD/DN): lãi flat actual/365 trên gốc ban đầu mỗi kỳ,
     * gốc trả theo quý (kỳ 3, 6, 9…); kỳ cuối trả hết dư gốc còn lại.
     */
    private List<RepaymentSchedule> flatPrincipalQuarterly(BigDecimal principal, BigDecimal annualRate,
                                                           int termMonths, LocalDate fundedDate,
                                                           List<LocalDate> dueDates) {
        int quarters = (int) Math.ceil(termMonths / 3.0);
        BigDecimal perQuarter = money(principal.divide(BigDecimal.valueOf(quarters), 10, RoundingMode.HALF_UP));
        List<RepaymentSchedule> schedule = new ArrayList<>(termMonths);
        BigDecimal remaining = principal;

        for (int k = 1; k <= termMonths; k++) {
            LocalDate start   = (k == 1) ? fundedDate : dueDates.get(k - 2);
            LocalDate dueDate = dueDates.get(k - 1);
            long days         = ChronoUnit.DAYS.between(start, dueDate);

            BigDecimal interest = calcInterest(principal, annualRate, days);
            BigDecimal princ;
            if (k == termMonths)      princ = remaining;
            else if (k % 3 == 0)      princ = perQuarter.min(remaining);
            else                      princ = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            remaining = remaining.subtract(princ);
            schedule.add(buildPeriod(k, dueDate, money(princ), interest));
        }
        log.debug("Generated FLAT QUARTERLY schedule: {} kỳ, gốc theo quý {}, lãi flat actual/365 trên gốc {}",
                termMonths, perQuarter, principal);
        return schedule;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Lãi flat actual/365: gốc_ban_đầu × annualRate(%) × days / 36500 (chia một lần). */
    private BigDecimal calcInterest(BigDecimal principal, BigDecimal annualRate, long days) {
        return money(
            principal.multiply(annualRate)
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
                .totalDue(money(princ.add(interest)))
                .paidAmount(BigDecimal.ZERO)
                .status(RepaymentStatus.PENDING)
                .dpd(0)
                .build();
    }

    private BigDecimal money(BigDecimal v) {
        return v.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
