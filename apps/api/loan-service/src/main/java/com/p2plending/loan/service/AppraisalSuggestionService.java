package com.p2plending.loan.service;

import com.p2plending.loan.domain.entity.LoanProduct;
import com.p2plending.loan.domain.entity.LoanRequest;
import com.p2plending.loan.domain.entity.RepaymentSchedule;
import com.p2plending.loan.domain.enums.CreditBand;
import com.p2plending.loan.domain.enums.RepaymentMethod;
import com.p2plending.loan.domain.repository.LoanRequestRepository;
import com.p2plending.loan.dto.response.AppraisalSuggestionResponse;
import com.p2plending.loan.dto.response.AppraisalSuggestionResponse.*;
import com.p2plending.loan.exception.LoanNotFoundException;
import com.p2plending.loan.service.FundingRateCard.RateCell;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Engine hỗ trợ thẩm định (decision-support), Phase 0 — luật chuyên gia (expert-prior).
 *
 * <p>KHÔNG tự quyết định giải ngân. Sinh ra: điểm rủi ro + hạng tín nhiệm (A1–C3),
 * năng lực trả nợ, <b>số tiền &amp; lãi suất + phí đề xuất theo biểu QĐ-LSGV</b>,
 * xem trước lịch trả nợ, và checklist các mục thẩm định viên phải tự xác minh.
 *
 * <p>Lãi suất & phí lấy từ {@link FundingRateCard} theo (Nhóm sản phẩm × Hạng tín nhiệm).
 * Mọi chỉ tiêu tài chính hiện là DỮ LIỆU TỰ KHAI, chưa xác minh — giá trị thực nằm ở
 * checklist thủ công.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AppraisalSuggestionService {

    private static final ZoneId TZ = ZoneId.of("Asia/Ho_Chi_Minh");
    /** Làm tròn số tiền đề xuất xuống bội số gần nhất (100.000 VND). */
    private static final BigDecimal AMOUNT_UNIT = new BigDecimal("100000");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final LoanRequestRepository loanRequestRepository;
    private final LoanProductService loanProductService;
    private final RepaymentScheduleGenerator generator;
    private final FundingRateCard rateCard;

    /** Trần tỷ lệ trả nợ / thu nhập (PTI). */
    @Value("${app.appraisal.pti-cap:0.40}")
    private BigDecimal ptiCap;

    /** Lãi tham chiếu (%/năm) để ước lượng PTI khi chấm điểm (độc lập với hạng). */
    @Value("${app.appraisal.reference-rate:19.0}")
    private BigDecimal referenceRate;

    /** Điều 3 — phụ phí lĩnh vực không khuyến khích (+%/năm). */
    @Value("${app.appraisal.discouraged-surcharge:2.0}")
    private BigDecimal discouragedSurcharge;

    /** Trần lãi suất theo pháp luật (%/năm) — biểu QĐ-LSGV: 20%. */
    @Value("${app.appraisal.legal-max-rate:20.0}")
    private BigDecimal legalMaxRate;

    @Transactional(readOnly = true)
    public AppraisalSuggestionResponse suggest(String loanId, boolean discouragedSector, String creditGrade) {
        LoanRequest loan = loanRequestRepository.findById(loanId)
                .orElseThrow(() -> new LoanNotFoundException(loanId));

        LoanProduct product = (loan.getProductId() == null)
                ? null
                : loanProductService.findProductById(loan.getProductId()).orElse(null);
        int group = product != null ? product.getProductGroup() : 2;
        boolean professionBound = product != null && product.isProfessionBound();

        BigDecimal requested = loan.getAmount();
        int term = loan.getTermMonths();
        BigDecimal income = loan.getMonthlyIncome();
        boolean hasIncome = income != null && income.signum() > 0;

        // 1) Bậc giá lấy từ HẠNG CREDIT SCORE 360 (chuẩn đánh giá duy nhất), KHÔNG tự chấm.
        //    null = chưa chấm điểm Credit 360 → chưa định giá được.
        CreditBand band = mapCredit360ToBand(creditGrade);

        // 2) Tra biểu lãi suất & phí theo (nhóm × bậc giá)
        boolean available = false;
        BigDecimal suggestedRate = null;
        BigDecimal feePercent = null;
        if (band != null) {
            RateCell cell = rateCard.lookup(group, band);
            available = cell.available();
            if (available) {
                suggestedRate = cell.annualRate();
                if (discouragedSector) suggestedRate = suggestedRate.add(discouragedSurcharge);
                suggestedRate = suggestedRate.min(legalMaxRate).setScale(2, RoundingMode.HALF_UP);
                feePercent = cell.feePercent();
            }
        }
        // Lãi dùng cho tính toán năng lực trả nợ (fallback lãi tham chiếu nếu chưa cấp)
        BigDecimal calcRate = available ? suggestedRate : referenceRate;

        // 4) Năng lực trả nợ
        BigDecimal requestedInstallment = annuityInstallment(requested, calcRate, term);
        BigDecimal requestedPti = hasIncome ? ratio(requestedInstallment, income) : null;
        BigDecimal maxInstallmentByIncome = hasIncome ? money(income.multiply(ptiCap)) : null;
        BigDecimal maxPrincipalByIncome = hasIncome
                ? inverseAnnuityPrincipal(maxInstallmentByIncome, calcRate, term) : null;

        // 5) Số tiền đề xuất = MIN các ràng buộc
        AmountResult amountResult = computeSuggestedAmount(
                requested, band, available, product, maxPrincipalByIncome, hasIncome);

        // 6) Phí giải ngân theo số tiền đề xuất
        BigDecimal connectionFee = (available && feePercent != null && amountResult.amount.signum() > 0)
                ? money(amountResult.amount.multiply(feePercent).divide(HUNDRED, 10, RoundingMode.HALF_UP))
                : null;

        // 7) Xem trước lịch trả nợ ở số tiền & lãi đề xuất
        RepaymentMethod method = resolveMethod(product);
        SchedulePreview preview = (available && amountResult.amount.signum() > 0)
                ? buildPreview(amountResult.amount, suggestedRate, term, method) : null;

        // 8) Cảnh báo + checklist (không còn khuyến nghị engine — Credit 360 là chuẩn duy nhất)
        List<String> warnings = buildWarnings(loan, group, band, available, discouragedSector,
                hasIncome, requestedPti, amountResult, requested, term, professionBound);
        List<ChecklistItem> checklist = buildChecklist(loan, product);

        String rateNote;
        if (available) {
            rateNote = "Lãi suất tối thiểu theo biểu nhóm %d · bậc giá %s (ánh xạ từ hạng Credit 360). Thực tế thoả thuận giữa nhà đầu tư & người gọi vốn, không vượt %s%%/năm."
                    .formatted(group, band, plain(legalMaxRate));
        } else if (band == null) {
            rateNote = "Cần chấm điểm Credit 360 trước khi đề xuất lãi suất & hạn mức.";
        } else {
            rateNote = "Bậc giá %s — không cấp dịch vụ gọi vốn cho nhóm sản phẩm %d theo biểu QĐ-LSGV.".formatted(band, group);
        }

        return AppraisalSuggestionResponse.builder()
                .loanId(loan.getId())
                .loanCode(loan.getLoanCode())
                .status(loan.getStatus())
                .requestedAmount(requested)
                .termMonths(term)
                .productGroup(group)
                .productName(product != null ? product.getName() : null)
                .affordability(Affordability.builder()
                        .incomeProvided(hasIncome)
                        .monthlyIncome(income)
                        .ptiCap(ptiCap)
                        .requestedInstallment(requestedInstallment)
                        .requestedPti(requestedPti)
                        .maxInstallmentByIncome(maxInstallmentByIncome)
                        .maxPrincipalByIncome(maxPrincipalByIncome)
                        .build())
                .recommendation(Recommendation.builder()
                        .suggestedAmount(amountResult.amount)
                        .amountCapReason(amountResult.reason)
                        .suggestedInterestRate(suggestedRate)
                        .suggestedRateMin(suggestedRate)
                        .suggestedRateMax(available ? legalMaxRate.setScale(2, RoundingMode.HALF_UP) : null)
                        .feePercent(feePercent)
                        .connectionFee(connectionFee)
                        .serviceAvailable(available)
                        .rateNote(rateNote)
                        .build())
                .schedulePreview(preview)
                .manualChecklist(checklist)
                .autoWarnings(warnings)
                .disclaimer("Gợi ý mang tính hỗ trợ (expert-prior, Phase 0). Lãi suất & phí lấy từ biểu QĐ-LSGV "
                        + "theo nhóm sản phẩm × hạng tín nhiệm. Mọi chỉ tiêu tài chính là tự khai, phải xác minh "
                        + "theo checklist và đối chiếu Điểm tín dụng tham khảo (300–850) cùng kết quả AI thẩm định chứng từ "
                        + "trước khi trình ban lãnh đạo. Quyết định cuối thuộc thẩm định viên & ban lãnh đạo.")
                .build();
    }

    // ── Ánh xạ hạng Credit 360 → bậc giá QĐ-LSGV ───────────────────

    /**
     * Ánh xạ hạng Credit Score 360 (A+..E, chuẩn đánh giá duy nhất) sang bậc giá
     * trong biểu QĐ-LSGV (A1..C3) để tra lãi suất/phí. Giữ NGUYÊN biểu giá pháp lý,
     * chỉ đổi nguồn hạng. null/không nhận diện → chưa định giá được (chưa chấm điểm).
     */
    private CreditBand mapCredit360ToBand(String creditGrade) {
        if (creditGrade == null || creditGrade.isBlank()) return null;
        return switch (creditGrade.trim().toUpperCase()) {
            case "A+" -> CreditBand.A1;
            case "A"  -> CreditBand.A2;
            case "B"  -> CreditBand.B1;
            case "C"  -> CreditBand.B3;
            case "D"  -> CreditBand.C1;
            case "E"  -> CreditBand.C3;
            default   -> null;
        };
    }

    // ── Số tiền đề xuất ────────────────────────────────────────────

    private record AmountResult(BigDecimal amount, String reason) {}

    private AmountResult computeSuggestedAmount(BigDecimal requested, CreditBand band, boolean available,
                                               LoanProduct product, BigDecimal maxPrincipalByIncome, boolean hasIncome) {
        if (!available) {
            String reason = band == null
                    ? "Chưa chấm điểm Credit 360 — chưa đề xuất được hạn mức"
                    : "Không cấp dịch vụ gọi vốn (bậc giá " + band + ")";
            return new AmountResult(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), reason);
        }
        BigDecimal amount = requested;
        String reason = "Theo số tiền yêu cầu";

        if (hasIncome && maxPrincipalByIncome != null && maxPrincipalByIncome.compareTo(amount) < 0) {
            amount = maxPrincipalByIncome;
            reason = "Giới hạn bởi năng lực trả nợ (thu nhập)";
        }

        BigDecimal bandCap = requested.multiply(bandCapFactor(band));
        if (bandCap.compareTo(amount) < 0) {
            amount = bandCap;
            reason = "Giới hạn bởi bậc giá " + band;
        }

        if (product != null && product.getMaxAmount() != null && product.getMaxAmount().compareTo(amount) < 0) {
            amount = product.getMaxAmount();
            reason = "Giới hạn bởi trần sản phẩm";
        }

        return new AmountResult(floorToUnit(amount), reason);
    }

    /** Hệ số trần dư nợ theo hạng tín nhiệm — hạng càng thấp cho vay càng ít so với yêu cầu. */
    private BigDecimal bandCapFactor(CreditBand band) {
        return switch (band) {
            case A1, A2, A3 -> new BigDecimal("1.00");
            case B1 -> new BigDecimal("0.90");
            case B2 -> new BigDecimal("0.80");
            case B3 -> new BigDecimal("0.70");
            case C1 -> new BigDecimal("0.60");
            case C2 -> new BigDecimal("0.50");
            case C3 -> BigDecimal.ZERO;
        };
    }

    // ── Cảnh báo tự động ───────────────────────────────────────────

    private List<String> buildWarnings(LoanRequest loan, int group, CreditBand band, boolean available,
                                       boolean discouraged, boolean hasIncome, BigDecimal requestedPti,
                                       AmountResult amountResult, BigDecimal requested, int term,
                                       boolean professionBound) {
        List<String> w = new ArrayList<>();
        if (band == null) {
            w.add("Chưa chấm điểm Credit 360 — chấm điểm trước để đề xuất lãi suất & hạn mức.");
        } else if (!available) {
            w.add("Bậc giá " + band + " không được cấp dịch vụ gọi vốn cho nhóm sản phẩm "
                    + group + " theo biểu — đề xuất từ chối.");
        }
        if (!hasIncome) {
            w.add("Người gọi vốn chưa khai thu nhập — bắt buộc thu thập & xác minh trước khi đề xuất.");
        }
        if (!StringUtils.hasText(loan.getRef1Phone()) && !StringUtils.hasText(loan.getRef2Phone())) {
            w.add("Thiếu thông tin người tham chiếu.");
        }
        if (requestedPti != null && requestedPti.compareTo(new BigDecimal("0.50")) > 0) {
            w.add("Tỷ lệ trả nợ/thu nhập vượt 50% — vượt khả năng chi trả ở số tiền yêu cầu.");
        }
        boolean occupationKnown = professionBound || StringUtils.hasText(loan.getOccupation());
        if (!occupationKnown || !StringUtils.hasText(loan.getWorkplace())) {
            w.add(professionBound ? "Thiếu thông tin nơi làm việc."
                    : "Thiếu thông tin nghề nghiệp hoặc nơi làm việc.");
        }
        if (loan.getProductId() == null) {
            w.add("Khoản gọi vốn chưa gắn sản phẩm — áp dụng biểu nhóm 2 (mặc định).");
        }
        if (term >= 12) {
            w.add("Kỳ hạn ≥ 12 tháng — ngoài phạm vi biểu lãi suất ngắn hạn (Điều 1.3), cần trình duyệt lãi suất riêng.");
        }
        if (discouraged) {
            w.add("Đã áp phụ phí +" + plain(discouragedSurcharge) + "%/năm cho lĩnh vực không khuyến khích (Điều 3).");
        }
        if (available && amountResult.amount.compareTo(requested) < 0) {
            w.add("Số tiền đề xuất (" + plain(amountResult.amount) + " VND) thấp hơn yêu cầu ("
                    + plain(requested) + " VND) — " + amountResult.reason.toLowerCase() + ".");
        }
        return w;
    }

    // ── Checklist thẩm định thủ công ───────────────────────────────

    private List<ChecklistItem> buildChecklist(LoanRequest loan, LoanProduct product) {
        List<ChecklistItem> items = new ArrayList<>();
        boolean professionBound = product != null && product.isProfessionBound();

        items.add(item("VERIFY_IDENTITY", "IDENTITY", "Đối chiếu định danh eKYC",
                "Soát lại ảnh CCCD, khớp khuôn mặt, CCCD còn hạn (hệ thống đã tự đối chiếu — kiểm tra các cờ cảnh báo).",
                true));

        items.add(item("VERIFY_INCOME", "INCOME", "Xác minh thu nhập",
                "Đối chiếu chứng từ thu nhập đã tải lên và kết quả AI thẩm định chứng từ (khối Điểm tín dụng tham khảo). "
                        + "Nếu chưa có chứng từ, yêu cầu sao kê lương/bảng lương 3 tháng gần nhất hoặc giấy phép kinh doanh. Mức tự khai: "
                        + (loan.getMonthlyIncome() != null ? plain(loan.getMonthlyIncome()) + " VND/tháng." : "CHƯA KHAI."),
                true));

        if (professionBound) {
            items.add(item("VERIFY_PROFESSION", "EMPLOYMENT", "Xác minh đúng đối tượng sản phẩm",
                    "Sản phẩm \"" + product.getName() + "\" ràng buộc theo nghề/đối tượng. Yêu cầu BẰNG CHỨNG đúng đối tượng "
                            + "(vd: chứng chỉ hành nghề, thẻ ngành, quyết định công tác, thẻ sinh viên). Đối chiếu nơi làm việc: "
                            + orDash(loan.getWorkplace()) + ".",
                    true));
        } else {
            items.add(item("VERIFY_EMPLOYMENT", "EMPLOYMENT", "Xác minh nghề nghiệp & nơi làm việc",
                    "Gọi/kiểm tra nơi làm việc: " + orDash(loan.getWorkplace())
                            + ". Xác nhận nghề nghiệp tự khai: " + orDash(loan.getOccupation()) + ".",
                    true));
        }

        if (StringUtils.hasText(loan.getRef1Phone()) || StringUtils.hasText(loan.getRef1FullName())) {
            items.add(item("CALL_REF1", "REFERENCE", "Gọi người tham chiếu 1",
                    "Gọi " + orDash(loan.getRef1FullName()) + " (" + orDash(loan.getRef1Relationship()) + ") — "
                            + orDash(loan.getRef1Phone()) + ". Xác nhận quan hệ & sự đồng ý bảo lãnh.",
                    true));
        }
        if (StringUtils.hasText(loan.getRef2Phone()) || StringUtils.hasText(loan.getRef2FullName())) {
            items.add(item("CALL_REF2", "REFERENCE", "Gọi người tham chiếu 2",
                    "Gọi " + orDash(loan.getRef2FullName()) + " (" + orDash(loan.getRef2Relationship()) + ") — "
                            + orDash(loan.getRef2Phone()) + ". Xác nhận quan hệ & sự đồng ý bảo lãnh.",
                    false));
        }

        items.add(item("ASSESS_PURPOSE", "PURPOSE", "Đánh giá mục đích gọi vốn",
                "Mục đích: \"" + orDash(loan.getPurpose()) + "\". Đánh giá tính hợp lý so với số tiền "
                        + plain(loan.getAmount()) + " VND và tính hợp pháp.",
                true));

        items.add(item("REVIEW_DOCS", "DOCUMENT", "Đối chiếu chứng từ bổ sung",
                "AI đã tự phân tích từng chứng từ khi chấm điểm tín dụng — đối chiếu verdict và cảnh báo của AI, "
                        + "mở file gốc soát dấu hiệu chỉnh sửa/giả mạo trước khi kết luận, đặc biệt các file AI đánh dấu cần kiểm tra.",
                true));

        items.add(item("FRAUD_CHECK", "FRAUD", "Rà soát dấu hiệu gian lận",
                "Câu chuyện có nhất quán? Hồ sơ có dấu hiệu được mớm? Đối chiếu kết quả kiểm tra trùng CCCD/blacklist.",
                true));

        return items;
    }

    // ── Lịch trả nợ xem trước ──────────────────────────────────────

    private SchedulePreview buildPreview(BigDecimal amount, BigDecimal rate, int term, RepaymentMethod method) {
        // Preview không có repaymentDay cụ thể → truyền null để dùng logic +1 tháng (legacy fallback)
        List<RepaymentSchedule> rows = generator.generate(amount, rate, term, method, LocalDate.now(TZ), null);
        BigDecimal totalPrincipal = BigDecimal.ZERO;
        BigDecimal totalInterest = BigDecimal.ZERO;
        for (RepaymentSchedule r : rows) {
            totalPrincipal = totalPrincipal.add(r.getPrincipalDue());
            totalInterest = totalInterest.add(r.getInterestDue());
        }
        return SchedulePreview.builder()
                .method(method)
                .periods(rows.size())
                .firstInstallment(rows.isEmpty() ? BigDecimal.ZERO : rows.get(0).getTotalDue())
                .totalPrincipal(money(totalPrincipal))
                .totalInterest(money(totalInterest))
                .totalPayable(money(totalPrincipal.add(totalInterest)))
                .build();
    }

    private RepaymentMethod resolveMethod(LoanProduct product) {
        if (product == null || product.getRepaymentMethod() == null) return RepaymentMethod.EMI_MONTHLY;
        return product.getRepaymentMethod();
    }

    // ── Toán tài chính ─────────────────────────────────────────────

    /** Niên kim: M = P·r·(1+r)^n / ((1+r)^n − 1). r = annualRate/1200. r=0 → P/n. */
    private BigDecimal annuityInstallment(BigDecimal principal, BigDecimal annualRate, int n) {
        double r = annualRate.doubleValue() / 1200.0;
        if (r == 0) return money(principal.divide(BigDecimal.valueOf(n), 10, RoundingMode.HALF_UP));
        double pow = Math.pow(1 + r, n);
        double m = principal.doubleValue() * r * pow / (pow - 1);
        return money(BigDecimal.valueOf(m));
    }

    /** Đảo niên kim: P = M·((1+r)^n − 1) / (r·(1+r)^n). r=0 → M·n. */
    private BigDecimal inverseAnnuityPrincipal(BigDecimal installment, BigDecimal annualRate, int n) {
        double r = annualRate.doubleValue() / 1200.0;
        if (r == 0) return money(installment.multiply(BigDecimal.valueOf(n)));
        double pow = Math.pow(1 + r, n);
        double p = installment.doubleValue() * (pow - 1) / (r * pow);
        return money(BigDecimal.valueOf(p));
    }

    private BigDecimal ratio(BigDecimal a, BigDecimal b) {
        return a.divide(b, 4, RoundingMode.HALF_UP);
    }

    private BigDecimal floorToUnit(BigDecimal v) {
        if (v.signum() <= 0) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        return v.divideToIntegralValue(AMOUNT_UNIT).multiply(AMOUNT_UNIT).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }


    private String plain(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }

    private String orDash(String s) {
        return StringUtils.hasText(s) ? s : "—";
    }

    // ── Factory ────────────────────────────────────────────────────

    private ChecklistItem item(String code, String category, String title, String instruction, boolean required) {
        return ChecklistItem.builder().code(code).category(category).title(title)
                .instruction(instruction).required(required).build();
    }
}
