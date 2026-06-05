package com.p2plending.loan.service;

import com.p2plending.loan.domain.enums.CreditBand;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

/**
 * Biểu lãi suất gọi vốn & phí giải ngân theo (Nhóm sản phẩm × Hạng tín nhiệm),
 * mã hoá đúng theo Quyết định .../QĐ-LSGV/21 của Chủ tịch HĐQT VNFITE (Điều 2).
 *
 * <ul>
 *   <li><b>annualRate</b>: lãi suất gọi vốn TỐI THIỂU (%/năm).</li>
 *   <li><b>feePercent</b>: phí kết nối (giải ngân) thành công, % trên giá trị giải ngân.</li>
 *   <li><b>available=false</b>: ô "Không cấp dịch vụ gọi vốn qua VNFITE".</li>
 * </ul>
 *
 * Biểu này thay đổi theo từng thời kỳ — khi có quyết định mới chỉ cần sửa tại đây.
 */
@Component
public class FundingRateCard {

    /** Một ô trong biểu: lãi suất tối thiểu + phí giải ngân, hoặc không cấp dịch vụ. */
    public record RateCell(BigDecimal annualRate, BigDecimal feePercent, boolean available) {
        static RateCell of(String rate, String fee) {
            return new RateCell(new BigDecimal(rate), new BigDecimal(fee), true);
        }
        static RateCell unavailable() {
            return new RateCell(null, null, false);
        }
    }

    private final Map<Integer, Map<CreditBand, RateCell>> card = new java.util.HashMap<>();

    public FundingRateCard() {
        // ── Nhóm 1: SPGV01 siêu tốc, SPGV02 sinh viên ──
        Map<CreditBand, RateCell> g1 = band();
        g1.put(CreditBand.A1, RateCell.of("18.20", "9"));
        g1.put(CreditBand.A2, RateCell.of("18.50", "10"));
        g1.put(CreditBand.A3, RateCell.of("18.80", "11"));
        g1.put(CreditBand.B1, RateCell.of("19.10", "12"));
        g1.put(CreditBand.B2, RateCell.of("19.40", "13"));
        g1.put(CreditBand.B3, RateCell.of("19.70", "14"));
        g1.put(CreditBand.C1, RateCell.unavailable());
        g1.put(CreditBand.C2, RateCell.unavailable());
        g1.put(CreditBand.C3, RateCell.unavailable());
        card.put(1, g1);

        // ── Nhóm 2: SPGV03–14 (hoá đơn, tiêu dùng, bảo hiểm, thẻ, công nhân viên, tài xế, thẩm mỹ...) ──
        Map<CreditBand, RateCell> g2 = band();
        g2.put(CreditBand.A1, RateCell.of("18.00", "7"));
        g2.put(CreditBand.A2, RateCell.of("18.30", "8"));
        g2.put(CreditBand.A3, RateCell.of("18.60", "9"));
        g2.put(CreditBand.B1, RateCell.of("18.90", "10"));
        g2.put(CreditBand.B2, RateCell.of("19.20", "11"));
        g2.put(CreditBand.B3, RateCell.of("19.50", "12"));
        g2.put(CreditBand.C1, RateCell.of("19.80", "13"));
        g2.put(CreditBand.C2, RateCell.unavailable());
        g2.put(CreditBand.C3, RateCell.unavailable());
        card.put(2, g2);

        // ── Nhóm 3: SPGV15 bác sĩ, SPGV16 giảng viên/giáo viên, SPGV17 cán bộ LLVT ──
        Map<CreditBand, RateCell> g3 = band();
        g3.put(CreditBand.A1, RateCell.of("18.00", "6"));
        g3.put(CreditBand.A2, RateCell.of("18.30", "7"));
        g3.put(CreditBand.A3, RateCell.of("18.60", "8"));
        g3.put(CreditBand.B1, RateCell.of("18.90", "9"));
        g3.put(CreditBand.B2, RateCell.of("19.10", "10"));
        g3.put(CreditBand.B3, RateCell.of("19.40", "11"));
        g3.put(CreditBand.C1, RateCell.of("19.70", "12"));
        g3.put(CreditBand.C2, RateCell.of("20.00", "13"));
        g3.put(CreditBand.C3, RateCell.unavailable());
        card.put(3, g3);

        // ── Nhóm 4: SPGV18 hộ KD tiểu thương, SPGV19 hộ KD chợ/TTTM, SPGV20 DN vừa và nhỏ ──
        Map<CreditBand, RateCell> g4 = band();
        g4.put(CreditBand.A1, RateCell.of("16.50", "9"));
        g4.put(CreditBand.A2, RateCell.of("17.00", "9.5"));
        g4.put(CreditBand.A3, RateCell.of("17.50", "10"));
        g4.put(CreditBand.B1, RateCell.of("18.00", "10.5"));
        g4.put(CreditBand.B2, RateCell.of("18.50", "11.1"));
        g4.put(CreditBand.B3, RateCell.of("19.00", "12"));
        g4.put(CreditBand.C1, RateCell.of("19.50", "12.5"));
        g4.put(CreditBand.C2, RateCell.of("20.00", "13"));
        g4.put(CreditBand.C3, RateCell.unavailable());
        card.put(4, g4);
    }

    private Map<CreditBand, RateCell> band() {
        return new EnumMap<>(CreditBand.class);
    }

    /**
     * Tra ô biểu theo nhóm sản phẩm & hạng tín nhiệm.
     * Nhóm không hợp lệ → mặc định nhóm 2 (tiêu dùng).
     */
    public RateCell lookup(int productGroup, CreditBand band) {
        Map<CreditBand, RateCell> group = card.getOrDefault(productGroup, card.get(2));
        return group.getOrDefault(band, RateCell.unavailable());
    }
}
