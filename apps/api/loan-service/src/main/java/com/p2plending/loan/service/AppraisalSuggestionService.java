package com.p2plending.loan.service;

import com.p2plending.loan.domain.entity.LoanProduct;
import com.p2plending.loan.domain.entity.LoanRequest;
import com.p2plending.loan.domain.entity.RepaymentSchedule;
import com.p2plending.loan.domain.enums.CreditBand;
import com.p2plending.loan.domain.enums.RecommendedDecision;
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
    private static final BigDecimal MONTHS_PER_YEAR = BigDecimal.valueOf(12);
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
    public AppraisalSuggestionResponse suggest(String loanId, boolean discouragedSector) {
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

        // 1) Burden phục vụ CHẤM ĐIỂM theo lãi tham chiếu (độc lập với hạng → tránh vòng lặp)
        BigDecimal scoringInstallment = annuityInstallment(requested, referenceRate, term);
        BigDecimal scoringPti = hasIncome ? ratio(scoringInstallment, income) : null;

        // 2) Chấm điểm expert-prior → hạng tín nhiệm A1..C3
        List<ScoreFactor> factors = new ArrayList<>();
        int score = computeScore(loan, requested, term, income, hasIncome, scoringPti, professionBound, factors);
        CreditBand band = bandOf(score);

        // 3) Tra biểu lãi suất & phí theo (nhóm × hạng)
        RateCell cell = rateCard.lookup(group, band);
        boolean available = cell.available();

        BigDecimal suggestedRate = null;
        BigDecimal feePercent = null;
        if (available) {
            suggestedRate = cell.annualRate();
            if (discouragedSector) suggestedRate = suggestedRate.add(discouragedSurcharge);
            suggestedRate = suggestedRate.min(legalMaxRate).setScale(2, RoundingMode.HALF_UP);
            feePercent = cell.feePercent();
        }
        // Lãi dùng cho tính toán năng lực trả nợ (fallback lãi tham chiếu nếu không cấp)
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

        // 8) Khuyến nghị + cảnh báo + checklist
        RecommendedDecision decision = decide(band, available, hasIncome, requestedPti, amountResult.amount);
        List<String> warnings = buildWarnings(loan, group, band, available, discouragedSector,
                hasIncome, requestedPti, amountResult, requested, term, professionBound);
        List<ChecklistItem> checklist = buildChecklist(loan, product);

        String rateNote = available
                ? "Lãi suất tối thiểu theo biểu nhóm %d · hạng %s. Thực tế thoả thuận giữa nhà đầu tư & người gọi vốn, không vượt %s%%/năm."
                        .formatted(group, band, plain(legalMaxRate))
                : "Hạng %s — không cấp dịch vụ gọi vốn cho nhóm sản phẩm %d theo biểu QĐ-LSGV.".formatted(band, group);

        return AppraisalSuggestionResponse.builder()
                .loanId(loan.getId())
                .loanCode(loan.getLoanCode())
                .status(loan.getStatus())
                .requestedAmount(requested)
                .termMonths(term)
                .productGroup(group)
                .productName(product != null ? product.getName() : null)
                .risk(RiskAssessment.builder().score(score).band(band).factors(factors).build())
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
                        .decision(decision)
                        .build())
                .schedulePreview(preview)
                .manualChecklist(checklist)
                .autoWarnings(warnings)
                .disclaimer("Gợi ý mang tính hỗ trợ (expert-prior, Phase 0). Lãi suất & phí lấy từ biểu QĐ-LSGV "
                        + "theo nhóm sản phẩm × hạng tín nhiệm. Mọi chỉ tiêu tài chính là tự khai, phải xác minh "
                        + "theo checklist trước khi trình ban lãnh đạo. Quyết định cuối thuộc thẩm định viên & ban lãnh đạo.")
                .build();
    }

    // ── Chấm điểm ──────────────────────────────────────────────────

    private int computeScore(LoanRequest loan, BigDecimal requested, int term, BigDecimal income,
                             boolean hasIncome, BigDecimal scoringPti, boolean professionBound,
                             List<ScoreFactor> factors) {
        int score = 50; // baseline trung lập

        // (1) PTI — trọng số lớn nhất
        if (!hasIncome) {
            factors.add(factor("PTI", "Khả năng trả nợ", "NEUTRAL", 0,
                    "Chưa khai thu nhập — không đánh giá được năng lực trả nợ."));
        } else {
            double pti = scoringPti.doubleValue();
            int pts;
            String impact, detail;
            if (pti <= 0.30)      { pts = 20;  impact = "POSITIVE"; detail = "PTI ≤ 30% — gánh nặng trả nợ thấp."; }
            else if (pti <= 0.40) { pts = 8;   impact = "NEUTRAL";  detail = "PTI 30–40% — chấp nhận được."; }
            else if (pti <= 0.50) { pts = -10; impact = "NEGATIVE"; detail = "PTI 40–50% — gánh nặng cao."; }
            else                  { pts = -25; impact = "NEGATIVE"; detail = "PTI > 50% — vượt khả năng chi trả."; }
            score += pts;
            factors.add(factor("PTI", "Tỷ lệ trả nợ / thu nhập", impact, pts,
                    detail + " (PTI ≈ " + pct(scoringPti) + ")"));
        }

        // (2) Số tiền vay so với thu nhập năm
        if (hasIncome) {
            BigDecimal annualIncome = income.multiply(MONTHS_PER_YEAR);
            double r = ratio(requested, annualIncome).doubleValue();
            int pts;
            String impact, detail;
            if (r <= 0.5)      { pts = 10;  impact = "POSITIVE"; detail = "Khoản vay ≤ 50% thu nhập năm."; }
            else if (r <= 1.0) { pts = 3;   impact = "NEUTRAL";  detail = "Khoản vay 0.5–1× thu nhập năm."; }
            else if (r <= 2.0) { pts = -5;  impact = "NEGATIVE"; detail = "Khoản vay 1–2× thu nhập năm."; }
            else               { pts = -12; impact = "NEGATIVE"; detail = "Khoản vay > 2× thu nhập năm."; }
            score += pts;
            factors.add(factor("AMOUNT_INCOME", "Khoản vay / thu nhập năm", impact, pts, detail));
        }

        // (3) Kỳ hạn
        int termPts;
        String termImpact, termDetail;
        if (term <= 6)       { termPts = 5;  termImpact = "POSITIVE"; termDetail = "Kỳ hạn ngắn (≤ 6 tháng)."; }
        else if (term <= 12) { termPts = 2;  termImpact = "NEUTRAL";  termDetail = "Kỳ hạn 7–12 tháng."; }
        else if (term <= 24) { termPts = 0;  termImpact = "NEUTRAL";  termDetail = "Kỳ hạn 13–24 tháng."; }
        else                 { termPts = -5; termImpact = "NEGATIVE"; termDetail = "Kỳ hạn dài (> 24 tháng)."; }
        score += termPts;
        factors.add(factor("TERM", "Kỳ hạn vay", termImpact, termPts, termDetail));

        // (4) Người tham chiếu
        int refCount = 0;
        if (StringUtils.hasText(loan.getRef1Phone())) refCount++;
        if (StringUtils.hasText(loan.getRef2Phone())) refCount++;
        int refPts;
        String refImpact, refDetail;
        if (refCount >= 2)      { refPts = 8;  refImpact = "POSITIVE"; refDetail = "Đủ 2 người tham chiếu."; }
        else if (refCount == 1) { refPts = 3;  refImpact = "NEUTRAL";  refDetail = "Chỉ 1 người tham chiếu."; }
        else                    { refPts = -8; refImpact = "NEGATIVE"; refDetail = "Thiếu người tham chiếu."; }
        score += refPts;
        factors.add(factor("REFERENCES", "Người tham chiếu", refImpact, refPts, refDetail));

        // (5) Đầy đủ hồ sơ nghề nghiệp (sản phẩm ràng buộc nghề → nghề do sản phẩm xác định)
        boolean occupationKnown = professionBound || StringUtils.hasText(loan.getOccupation());
        boolean profileComplete = occupationKnown && StringUtils.hasText(loan.getWorkplace());
        int profPts = profileComplete ? 5 : -3;
        String profDetail = profileComplete
                ? (professionBound ? "Nghề theo sản phẩm & có nơi làm việc." : "Có nghề nghiệp & nơi làm việc.")
                : "Thiếu nghề nghiệp hoặc nơi làm việc.";
        factors.add(factor("PROFILE", "Hồ sơ nghề nghiệp",
                profileComplete ? "POSITIVE" : "NEGATIVE", profPts, profDetail));
        score += profPts;

        // (6) Người giới thiệu
        if (StringUtils.hasText(loan.getReferredBy())) {
            score += 3;
            factors.add(factor("REFERRAL", "Người giới thiệu", "POSITIVE", 3, "Được giới thiệu qua kênh nội bộ."));
        }

        return Math.max(0, Math.min(100, score));
    }

    /** Ánh xạ điểm 0–100 → hạng tín nhiệm 9 bậc A1..C3. */
    private CreditBand bandOf(int score) {
        if (score >= 90) return CreditBand.A1;
        if (score >= 83) return CreditBand.A2;
        if (score >= 76) return CreditBand.A3;
        if (score >= 68) return CreditBand.B1;
        if (score >= 60) return CreditBand.B2;
        if (score >= 52) return CreditBand.B3;
        if (score >= 44) return CreditBand.C1;
        if (score >= 36) return CreditBand.C2;
        return CreditBand.C3;
    }

    // ── Số tiền đề xuất ────────────────────────────────────────────

    private record AmountResult(BigDecimal amount, String reason) {}

    private AmountResult computeSuggestedAmount(BigDecimal requested, CreditBand band, boolean available,
                                               LoanProduct product, BigDecimal maxPrincipalByIncome, boolean hasIncome) {
        if (!available) {
            return new AmountResult(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    "Không cấp dịch vụ gọi vốn (hạng " + band + ")");
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
            reason = "Giới hạn bởi hạng tín nhiệm " + band;
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

    // ── Khuyến nghị ────────────────────────────────────────────────

    private RecommendedDecision decide(CreditBand band, boolean available, boolean hasIncome,
                                       BigDecimal requestedPti, BigDecimal amount) {
        if (!available || amount.signum() <= 0) return RecommendedDecision.REJECT;
        if (!hasIncome) return RecommendedDecision.REVIEW;
        if (requestedPti != null && requestedPti.compareTo(ptiCap) > 0) return RecommendedDecision.REVIEW;
        // A1–B1 nghiêng duyệt, còn lại cần thẩm định kỹ
        return band.ordinal() <= CreditBand.B1.ordinal()
                ? RecommendedDecision.APPROVE : RecommendedDecision.REVIEW;
    }

    // ── Cảnh báo tự động ───────────────────────────────────────────

    private List<String> buildWarnings(LoanRequest loan, int group, CreditBand band, boolean available,
                                       boolean discouraged, boolean hasIncome, BigDecimal requestedPti,
                                       AmountResult amountResult, BigDecimal requested, int term,
                                       boolean professionBound) {
        List<String> w = new ArrayList<>();
        if (!available) {
            w.add("Hạng tín nhiệm " + band + " không được cấp dịch vụ gọi vốn cho nhóm sản phẩm "
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
                "Yêu cầu sao kê lương/bảng lương 3 tháng gần nhất hoặc giấy phép kinh doanh. Đối chiếu với mức khai: "
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

        items.add(item("ASSESS_PURPOSE", "PURPOSE", "Đánh giá mục đích vay",
                "Mục đích: \"" + orDash(loan.getPurpose()) + "\". Đánh giá tính hợp lý so với số tiền "
                        + plain(loan.getAmount()) + " VND và tính hợp pháp.",
                true));

        items.add(item("REVIEW_DOCS", "DOCUMENT", "Đối chiếu chứng từ bổ sung",
                "Soát các chứng từ khách cung cấp (cư trú, thu nhập, KD...), phát hiện dấu hiệu chỉnh sửa/giả mạo.",
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

    private String pct(BigDecimal ratio) {
        return ratio.multiply(HUNDRED).setScale(1, RoundingMode.HALF_UP) + "%";
    }

    private String plain(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }

    private String orDash(String s) {
        return StringUtils.hasText(s) ? s : "—";
    }

    // ── Factory ────────────────────────────────────────────────────

    private ScoreFactor factor(String code, String label, String impact, int points, String detail) {
        return ScoreFactor.builder().code(code).label(label).impact(impact).points(points).detail(detail).build();
    }

    private ChecklistItem item(String code, String category, String title, String instruction, boolean required) {
        return ChecklistItem.builder().code(code).category(category).title(title)
                .instruction(instruction).required(required).build();
    }
}
