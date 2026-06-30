package com.p2plending.loan.service;

import com.p2plending.loan.domain.entity.RepaymentSchedule;
import com.p2plending.loan.domain.enums.RepaymentMethod;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Khóa mô hình tính tiền mới: lãi FLAT trên gốc giải ngân ban đầu × rate × số ngày thực tế / 365,
 * gốc chia đều, actual/365, repaymentDay 5/20 (kỳ 1 pro-rate).
 */
class RepaymentScheduleGeneratorTest {

    private final RepaymentScheduleGenerator generator = new RepaymentScheduleGenerator();

    @Test
    void flatInterestOnOriginalPrincipal_equalPrincipal_actual365() {
        BigDecimal principal  = new BigDecimal("100000000"); // 100tr
        BigDecimal annualRate = new BigDecimal("18.00");     // 18%/năm
        int term = 12;
        LocalDate funded = LocalDate.of(2026, 1, 10);

        List<RepaymentSchedule> s = generator.generate(
                principal, annualRate, term, RepaymentMethod.EMI_MONTHLY, funded, 20);

        assertThat(s).hasSize(12);

        // Kỳ 1: 10/01 → 20/02 = 41 ngày; lãi = 100tr×18×41/36500
        assertThat(s.get(0).getDueDate()).isEqualTo(LocalDate.of(2026, 2, 20));
        assertThat(s.get(0).getInterestDue()).isEqualByComparingTo("2021918");
        assertThat(s.get(0).getPrincipalDue()).isEqualByComparingTo("8333333");

        // Kỳ 2: 20/02 → 20/03 = 28 ngày (tháng 2/2026) → lãi THẤP hơn kỳ 31 ngày
        assertThat(s.get(1).getInterestDue()).isEqualByComparingTo("1380822");

        // Kỳ 3: 20/03 → 20/04 = 31 ngày → lãi cao hơn kỳ 2 (chứng minh actual/365, không cố định)
        assertThat(s.get(2).getInterestDue()).isEqualByComparingTo("1528767");

        // Lãi tính trên GỐC BAN ĐẦU (flat): kỳ 2 và một kỳ 28-ngày bất kỳ bằng nhau,
        // KHÔNG giảm dần như annuity (kỳ 2 declining sẽ < kỳ 1 nhiều) → ở đây chỉ chênh do số ngày.
        assertThat(s.get(1).getInterestDue()).isLessThan(s.get(2).getInterestDue());

        // Gốc chia đều, kỳ cuối gánh phần dư, tổng gốc = đúng số vay
        assertThat(s.get(11).getPrincipalDue()).isEqualByComparingTo("8333337");
        BigDecimal totalPrincipal = s.stream()
                .map(RepaymentSchedule::getPrincipalDue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(totalPrincipal).isEqualByComparingTo("100000000");

        // totalDue mỗi kỳ = gốc + lãi
        assertThat(s.get(0).getTotalDue())
                .isEqualByComparingTo(s.get(0).getPrincipalDue().add(s.get(0).getInterestDue()));
    }

    @Test
    void firstPeriodProRated_repaymentDay5() {
        BigDecimal principal  = new BigDecimal("60000000");
        BigDecimal annualRate = new BigDecimal("24.00");
        LocalDate funded = LocalDate.of(2026, 3, 18);

        List<RepaymentSchedule> s = generator.generate(
                principal, annualRate, 6, RepaymentMethod.EMI_MONTHLY, funded, 5);

        // Kỳ 1 đáo hạn ngày 5 tháng kế tiếp
        assertThat(s.get(0).getDueDate()).isEqualTo(LocalDate.of(2026, 4, 5));
        // 18/03 → 05/04 = 18 ngày; lãi = 60tr×24×18/36500 = 710.137
        assertThat(s.get(0).getInterestDue()).isEqualByComparingTo("710137");
        assertThat(s).hasSize(6);
    }
}
