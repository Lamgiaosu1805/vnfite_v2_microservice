package com.p2plending.loan.service;

import com.p2plending.loan.domain.entity.RepaymentSchedule;
import com.p2plending.loan.domain.enums.RepaymentMethod;
import com.p2plending.loan.domain.enums.RepaymentStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Sinh lịch trả nợ từ điều khoản khoản vay. Thuần tính toán, không chạm DB.
 *
 * <p>Quy ước làm tròn: lãi mỗi kỳ = dư nợ × lãi tháng (làm tròn 2 số). Kỳ cuối trả hết
 * phần gốc còn lại để tổng gốc khớp tuyệt đối với số tiền vay (tránh sai lệch tích lũy).
 */
@Component
@Slf4j
public class RepaymentScheduleGenerator {

    private static final int MONEY_SCALE = 2;

    /**
     * @param principal  số tiền vay
     * @param annualRate lãi suất %/năm (vd 12.50)
     * @param termMonths số kỳ (tháng)
     * @param method     kiểu trả nợ của sản phẩm
     * @param fundedDate ngày khoản vay FUNDED — mốc tính kỳ đầu (kỳ k đáo hạn = fundedDate + k tháng)
     */
    public List<RepaymentSchedule> generate(BigDecimal principal, BigDecimal annualRate,
                                            int termMonths, RepaymentMethod method, LocalDate fundedDate) {
        BigDecimal monthlyRate = annualRate
                .divide(BigDecimal.valueOf(1200), 10, RoundingMode.HALF_UP); // /100 /12

        return switch (method) {
            case INTEREST_MONTHLY_PRINCIPAL_QUARTERLY ->
                    interestMonthlyPrincipalQuarterly(principal, monthlyRate, termMonths, fundedDate);
            case EMI_MONTHLY ->
                    equalInstallment(principal, monthlyRate, termMonths, fundedDate);
        };
    }

    /** Gốc + lãi đều mỗi kỳ (annuity). */
    private List<RepaymentSchedule> equalInstallment(BigDecimal principal, BigDecimal monthlyRate,
                                                     int n, LocalDate fundedDate) {
        BigDecimal emi = annuityPayment(principal, monthlyRate, n);
        List<RepaymentSchedule> schedule = new ArrayList<>(n);
        BigDecimal outstanding = principal;

        for (int k = 1; k <= n; k++) {
            BigDecimal interest = money(outstanding.multiply(monthlyRate));
            BigDecimal princ = (k == n) ? outstanding : money(emi.subtract(interest));
            // Bảo vệ: không để gốc vượt dư nợ ở kỳ áp chót do làm tròn
            if (princ.compareTo(outstanding) > 0) princ = outstanding;
            outstanding = outstanding.subtract(princ);
            schedule.add(buildPeriod(k, fundedDate.plusMonths(k), princ, interest));
        }
        return schedule;
    }

    /** Lãi đều hàng tháng, gốc chia đều theo quý (trả vào các kỳ chia hết cho 3). */
    private List<RepaymentSchedule> interestMonthlyPrincipalQuarterly(BigDecimal principal, BigDecimal monthlyRate,
                                                                      int n, LocalDate fundedDate) {
        int quarters = (int) Math.ceil(n / 3.0);
        BigDecimal principalPerQuarter = money(principal.divide(BigDecimal.valueOf(quarters), 10, RoundingMode.HALF_UP));
        List<RepaymentSchedule> schedule = new ArrayList<>(n);
        BigDecimal outstanding = principal;

        for (int k = 1; k <= n; k++) {
            BigDecimal interest = money(outstanding.multiply(monthlyRate));
            BigDecimal princ = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            if (k == n) {
                princ = outstanding;                 // kỳ cuối trả hết phần gốc còn lại
            } else if (k % 3 == 0) {
                princ = principalPerQuarter.min(outstanding);
            }
            outstanding = outstanding.subtract(princ);
            schedule.add(buildPeriod(k, fundedDate.plusMonths(k), princ, interest));
        }
        return schedule;
    }

    /** Công thức niên kim: M = P·r·(1+r)^n / ((1+r)^n − 1). r=0 → P/n. */
    private BigDecimal annuityPayment(BigDecimal principal, BigDecimal monthlyRate, int n) {
        if (monthlyRate.signum() == 0) {
            return money(principal.divide(BigDecimal.valueOf(n), 10, RoundingMode.HALF_UP));
        }
        double r = monthlyRate.doubleValue();
        double pow = Math.pow(1 + r, n);
        double emi = principal.doubleValue() * r * pow / (pow - 1);
        return money(BigDecimal.valueOf(emi));
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
