package com.p2plending.credit.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2plending.credit.config.AppProperties;
import com.p2plending.credit.domain.entity.*;
import com.p2plending.credit.domain.repository.*;
import com.p2plending.credit.dto.request.BorrowerProfileRequest;
import com.p2plending.credit.dto.request.EvaluateScoreRequest;
import com.p2plending.credit.dto.response.CreditScoreResponse;
import com.p2plending.credit.service.ai.AiRiskAssessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class CreditScoringService {

    private final BorrowerProfileRepository profileRepository;
    private final CreditScoreRepository scoreRepository;
    private final CreditScoreDetailRepository detailRepository;
    private final FeatureSnapshotRepository snapshotRepository;
    private final ScoringEngine scoringEngine;
    private final ScoreExplainer scoreExplainer;
    private final AiRiskAssessor aiRiskAssessor;
    private final DocumentAnalysisService documentAnalysisService;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Map OccupationCategory của loan-service (22 nghề) → 5 nhóm scorecard.
     * Giá trị đã là nhóm scorecard (GOV_EMPLOYEE...) thì giữ nguyên.
     */
    private static final Map<String, String> OCCUPATION_MAP = Map.ofEntries(
            Map.entry("CIVIL_SERVANT", "GOV_EMPLOYEE"),
            Map.entry("ARMED_FORCES", "GOV_EMPLOYEE"),
            Map.entry("TEACHER", "GOV_EMPLOYEE"),
            Map.entry("TEACHER_LECTURER", "GOV_EMPLOYEE"),
            Map.entry("HEALTHCARE", "GOV_EMPLOYEE"),
            Map.entry("OFFICE_STAFF", "SALARIED"),
            Map.entry("FACTORY_WORKER", "SALARIED"),
            Map.entry("MANAGER", "SALARIED"),
            Map.entry("SALES", "SALARIED"),
            Map.entry("ENGINEER", "SALARIED"),
            Map.entry("PROFESSIONAL", "SALARIED"),
            Map.entry("SERVICE_WORKER", "SALARIED"),
            Map.entry("DRIVER", "SALARIED"),
            Map.entry("BUSINESS_OWNER", "BUSINESS_OWNER"),
            Map.entry("HOUSEHOLD_BUSINESS", "BUSINESS_OWNER"),
            Map.entry("SELF_EMPLOYED", "FREELANCER"),
            Map.entry("FREELANCER", "FREELANCER"),
            Map.entry("MANUAL_LABOR", "FREELANCER"),
            Map.entry("FARMER", "FREELANCER"),
            Map.entry("HOMEMAKER", "OTHER"),
            Map.entry("RETIRED", "OTHER"),
            Map.entry("STUDENT", "OTHER"),
            Map.entry("OTHER", "OTHER")
    );

    /**
     * Mobile từng gửi label tiếng Việt thay vì enum code, ví dụ "Giáo viên, giảng viên".
     * Credit-service phải nhận cả code lẫn label để không chấm nhầm về OTHER cho dữ liệu đã tạo.
     */
    private static final Map<String, String> OCCUPATION_LABEL_MAP = Map.ofEntries(
            Map.entry("CAN BO CONG CHUC VIEN CHUC NHA NUOC", "GOV_EMPLOYEE"),
            Map.entry("QUAN DOI CONG AN LUC LUONG VU TRANG", "GOV_EMPLOYEE"),
            Map.entry("GIAO VIEN GIANG VIEN", "GOV_EMPLOYEE"),
            Map.entry("Y BAC SI NHAN VIEN Y TE", "GOV_EMPLOYEE"),
            Map.entry("NHAN VIEN VAN PHONG", "SALARIED"),
            Map.entry("CONG NHAN", "SALARIED"),
            Map.entry("LANH DAO QUAN LY", "SALARIED"),
            Map.entry("NHAN VIEN KINH DOANH BAN HANG", "SALARIED"),
            Map.entry("KY SU KY THUAT VIEN", "SALARIED"),
            Map.entry("NGHE CHUYEN MON LUAT SU KE TOAN KIEM TOAN", "SALARIED"),
            Map.entry("NGHE CHUYEN MON LUAT SU KE TOAN", "SALARIED"),
            Map.entry("LAO DONG NGANH DICH VU NHA HANG KHACH SAN BAN LE", "SALARIED"),
            Map.entry("LAO DONG NGANH DICH VU", "SALARIED"),
            Map.entry("TAI XE", "SALARIED"),
            Map.entry("CHU DOANH NGHIEP", "BUSINESS_OWNER"),
            Map.entry("HO KINH DOANH TIEU THUONG", "BUSINESS_OWNER"),
            Map.entry("KINH DOANH TU DO BUON BAN NHO", "FREELANCER"),
            Map.entry("LAO DONG TU DO", "FREELANCER"),
            Map.entry("LAO DONG PHO THONG", "FREELANCER"),
            Map.entry("NONG LAM NGU NGHIEP", "FREELANCER"),
            Map.entry("NOI TRO", "OTHER"),
            Map.entry("HUU TRI", "OTHER"),
            Map.entry("SINH VIEN HOC SINH", "OTHER"),
            Map.entry("KHAC", "OTHER")
    );

    private static final Set<String> SCORECARD_OCCUPATIONS =
            Set.of("GOV_EMPLOYEE", "SALARIED", "BUSINESS_OWNER", "FREELANCER", "OTHER");

    private static final Map<String, String> GRADE_POLICY = Map.of(
            "A+", "Rủi ro rất thấp — ưu tiên duyệt nhanh, điều kiện tốt nhất",
            "A", "Rủi ro thấp — đề xuất duyệt nhanh, lãi suất ưu đãi",
            "B", "Rủi ro khá thấp — đề xuất duyệt, điều kiện chuẩn",
            "C", "Rủi ro trung bình — thẩm định kỹ, cân nhắc giảm hạn mức",
            "D", "Rủi ro cao — yêu cầu bổ sung hồ sơ/chứng từ tài chính, giảm hạn mức",
            "E", "Rủi ro rất cao — đề xuất từ chối"
    );

    /** Loại chứng từ chứng minh thu nhập (nuôi tín hiệu C1 — mức xác minh thu nhập). */
    private static final Set<String> INCOME_DOC_TYPES = Set.of(
            "SALARY_STATEMENT", "PAYSLIP", "BANK_STATEMENT", "INVOICE", "SALES_LEDGER",
            "POS_STATEMENT", "PLATFORM_SALES_REPORT", "TAX_DOCUMENT", "OTHER_INCOME_PROOF");

    /** Loại chứng từ chứng minh nghề nghiệp/kinh doanh (nuôi tín hiệu E3). */
    private static final Set<String> OCCUPATION_DOC_TYPES = Set.of(
            "LABOR_CONTRACT", "EMPLOYMENT_CONTRACT", "BUSINESS_LICENSE");

    /** Sai lệch tối đa giữa thu nhập AI trích xuất và thu nhập khai báo để coi là "khớp". */
    private static final double INCOME_MATCH_TOLERANCE = 0.25;

    // ─── Borrower profile ─────────────────────────────────────────────────────

    @Transactional
    public BorrowerProfile upsertProfile(BorrowerProfileRequest req) {
        BorrowerProfile profile = profileRepository.findByUserIdAndIsDeletedFalse(req.getUserId())
                .orElseGet(() -> BorrowerProfile.builder().userId(req.getUserId()).build());

        profile.setOccupationType(req.getOccupationType());
        profile.setEmploymentYears(req.getEmploymentYears());
        profile.setMonthlyIncome(req.getMonthlyIncome());
        profile.setMaritalStatus(req.getMaritalStatus());
        profile.setDependentsCount(req.getDependentsCount());
        profile.setEducationLevel(req.getEducationLevel());
        profile.setExistingMonthlyDebt(req.getExistingMonthlyDebt());
        profile.setNotes(req.getNotes());

        return profileRepository.save(profile);
    }

    @Transactional(readOnly = true)
    public BorrowerProfile getProfile(String userId) {
        return profileRepository.findByUserIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new IllegalArgumentException("Chưa có hồ sơ tài chính cho user " + userId));
    }

    // ─── Scoring ──────────────────────────────────────────────────────────────

    @Transactional
    public CreditScoreResponse evaluate(EvaluateScoreRequest req) {
        BorrowerProfile profile = profileRepository.findByUserIdAndIsDeletedFalse(req.getUserId()).orElse(null);

        // AI phân tích toàn bộ chứng từ TRƯỚC — kết quả vừa nuôi tín hiệu chấm điểm
        // (C1 xác minh thu nhập, E3 chứng từ nghề, H2 toàn vẹn chứng từ) vừa đưa vào advisory.
        // Fail từng file không chặn chấm điểm.
        List<DocumentAnalysis> docAnalyses = List.of();
        try {
            docAnalyses = documentAnalysisService.analyzeForScoring(req);
        } catch (Exception e) {
            log.error("AI document analysis error (bỏ qua): {}", e.getMessage());
        }

        Map<String, Object> features = buildFeatures(req, profile, docAnalyses);
        ScoringEngine.EngineResult engine = scoringEngine.evaluate(features);

        int score = normalize(engine.getTotalPoints(), engine.getMaxPoints());
        String grade = gradeOf(score);

        // AI advisory — fail thì bỏ qua, không chặn chấm điểm
        AiRiskAssessor.AiRiskAssessment ai = null;
        try {
            ai = aiRiskAssessor.assess(buildAiContext(req, profile, engine, score, grade, docAnalyses));
        } catch (Exception e) {
            log.error("AI risk assessment error (bỏ qua): {}", e.getMessage());
        }

        // Score cũ của user → SUPERSEDED
        List<CreditScore> oldScores = scoreRepository.findByUserIdAndStatusAndIsDeletedFalse(req.getUserId(), "VALID");
        oldScores.forEach(s -> s.setStatus("SUPERSEDED"));
        scoreRepository.saveAll(oldScores);

        CreditScore entity = CreditScore.builder()
                .userId(req.getUserId())
                .loanRequestId(req.getLoanRequestId())
                .score(score)
                .grade(grade)
                .rawPoints(engine.getTotalPoints())
                .maxPoints(engine.getMaxPoints())
                .modelVersion(appProperties.getScoring().getModelVersion())
                .status("VALID")
                .aiSummary(ai != null ? ai.summary() : null)
                .aiRiskFlags(ai != null ? toJson(ai.riskFlags()) : null)
                .aiRecommendation(ai != null ? ai.recommendation() : null)
                .expiresAt(LocalDateTime.now().plusDays(appProperties.getScoring().getScoreTtlDays()))
                .build();
        entity = scoreRepository.save(entity);

        List<CreditScoreDetail> details = new ArrayList<>();
        for (ScoringEngine.CriteriaResult r : engine.getDetails()) {
            details.add(CreditScoreDetail.builder()
                    .creditScoreId(entity.getId())
                    .criteriaCode(r.getCriteriaCode())
                    .criteriaName(r.getCriteriaName())
                    .component(r.getComponent())
                    .rawValue(r.getRawValue())
                    .points(r.getPoints())
                    .maxPoints(r.getMaxPoints())
                    .build());
        }
        detailRepository.saveAll(details);

        // Snapshot features → training data tương lai
        Map<String, Object> snapshotData = new LinkedHashMap<>(features);
        snapshotData.put("_loanAmount", req.getLoanAmount());
        snapshotData.put("_termMonths", req.getTermMonths());
        snapshotData.put("_purpose", req.getPurpose());
        snapshotData.put("_score", score);
        snapshotData.put("_grade", grade);
        snapshotRepository.save(FeatureSnapshot.builder()
                .userId(req.getUserId())
                .loanRequestId(req.getLoanRequestId())
                .creditScoreId(entity.getId())
                .features(toJson(snapshotData))
                .build());

        log.info("Chấm điểm xong: userId={} loanRequestId={} score={} grade={} raw={}/{} missing={}",
                req.getUserId(), req.getLoanRequestId(), score, grade,
                engine.getTotalPoints(), engine.getMaxPoints(), engine.getMissingData());

        return toResponse(entity, details, engine.getMissingData(),
                ai != null ? ai.riskFlags() : null, docAnalyses);
    }

    @Transactional(readOnly = true)
    public CreditScoreResponse getLatest(String userId) {
        CreditScore entity = scoreRepository
                .findFirstByUserIdAndStatusAndIsDeletedFalseOrderByCreatedAtDesc(userId, "VALID")
                .orElseThrow(() -> new IllegalArgumentException("User " + userId + " chưa được chấm điểm"));

        List<CreditScoreDetail> details = detailRepository.findByCreditScoreIdAndIsDeletedFalse(entity.getId());
        List<DocumentAnalysis> docAnalyses = entity.getLoanRequestId() != null
                ? documentAnalysisService.listByLoan(entity.getLoanRequestId())
                : List.of();
        return toResponse(entity, details, null, fromJsonList(entity.getAiRiskFlags()), docAnalyses);
    }

    /** loan-service gọi khi khoản vay kết thúc → điền label cho training data */
    @Transactional
    public void recordOutcome(String userId, String loanRequestId, String outcome) {
        List<FeatureSnapshot> snapshots =
                snapshotRepository.findByUserIdAndLoanRequestIdAndIsDeletedFalse(userId, loanRequestId);
        LocalDateTime now = LocalDateTime.now();
        snapshots.forEach(s -> {
            s.setLoanOutcome(outcome);
            s.setOutcomeAt(now);
        });
        snapshotRepository.saveAll(snapshots);
        log.info("Ghi nhận outcome={} cho userId={} loanRequestId={} ({} snapshot)",
                outcome, userId, loanRequestId, snapshots.size());
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Dựng features theo khung Credit Score 360 — giai đoạn "dữ liệu hiện có".
     * Chỉ phát các tín hiệu lấy được từ hệ thống + file chứng từ AI; KHÔNG chấm
     * biến nhân khẩu học nhạy cảm (tuổi/hôn nhân/người phụ thuộc/học vấn) theo
     * nguyên tắc chống phân biệt đối xử của mô hình.
     *
     * Feature null (không lấy được) sẽ bị loại khỏi map → engine coi là "thiếu dữ liệu"
     * và gán ĐIỂM SÀN cho tiêu chí đó (không phạt về 0). Nhờ vậy khách mới chưa đủ
     * dữ liệu không bị kéo tụt hạng oan, nhưng vẫn được nhắc bổ sung để nâng điểm.
     */
    private Map<String, Object> buildFeatures(EvaluateScoreRequest req, BorrowerProfile profile,
                                              List<DocumentAnalysis> docAnalyses) {
        Map<String, Object> f = new LinkedHashMap<>();

        // Thu nhập/nghề nghiệp/nợ: ưu tiên dữ liệu từ chính đơn gọi vốn, fallback borrower_profiles
        BigDecimal monthlyIncome = req.getMonthlyIncome() != null ? req.getMonthlyIncome()
                : (profile != null ? profile.getMonthlyIncome() : null);
        String occupation = req.getOccupation() != null ? req.getOccupation()
                : (profile != null ? profile.getOccupationType() : null);
        BigDecimal existingDebt = req.getExistingMonthlyDebt() != null ? req.getExistingMonthlyDebt()
                : (profile != null ? profile.getExistingMonthlyDebt() : null);
        BigDecimal employmentYears = profile != null ? profile.getEmploymentYears() : null;

        // ── A · KYC & định danh ──
        f.put("KYC_STATUS", req.getKycStatus());

        // ── B · Lịch sử tín dụng ──
        // Proxy nội bộ VNFITE (luôn có)
        f.put("COMPLETED_LOANS", req.getCompletedLoanCount());
        // CIC nhập tay (chờ API NĐ94) — null thì nhóm B báo thiếu để nhắc tra cứu
        f.put("CIC_DEBT_GROUP", req.getCicDebtGroup());
        f.put("CIC_MAX_DPD", req.getCicMaxDpd());
        f.put("CIC_ACTIVE_LENDERS", req.getCicActiveLenders());

        // ── C · Khả năng trả nợ ──
        // C1 — mức xác minh thu nhập (khai báo + kết quả AI đọc chứng từ thu nhập)
        f.put("INCOME_VERIFICATION", incomeVerificationLevel(monthlyIncome, req.getDocuments(), docAnalyses));
        // C2 — PTI: nghĩa vụ trả nợ kỳ (EMI) / thu nhập tháng
        if (isPositive(monthlyIncome) && isPositive(req.getLoanAmount()) && req.getTermMonths() != null
                && req.getTermMonths() > 0) {
            BigDecimal emi = monthlyInstallment(req.getLoanAmount(), req.getInterestRate(), req.getTermMonths());
            f.put("PTI_RATIO", emi.multiply(BigDecimal.valueOf(100))
                    .divide(monthlyIncome, 2, RoundingMode.HALF_UP));
        }
        // C3 — DTI: tổng nghĩa vụ nợ hiện hữu / thu nhập
        if (isPositive(monthlyIncome) && existingDebt != null) {
            f.put("DTI_RATIO", existingDebt.multiply(BigDecimal.valueOf(100))
                    .divide(monthlyIncome, 2, RoundingMode.HALF_UP));
        }

        // ── E · Nghề nghiệp & ổn định thu nhập ──
        f.put("EMPLOYMENT_YEARS", employmentYears);
        f.put("OCCUPATION_TYPE", mapOccupation(occupation));
        f.put("OCCUPATION_DOC", occupationDocLevel(req.getDocuments(), docAnalyses));

        // ── F · Đặc điểm khoản vay & quan hệ KH ──
        if (isPositive(monthlyIncome) && isPositive(req.getLoanAmount())) {
            BigDecimal annualIncome = monthlyIncome.multiply(BigDecimal.valueOf(12));
            f.put("LOAN_TO_ANNUAL_INCOME", req.getLoanAmount().multiply(BigDecimal.valueOf(100))
                    .divide(annualIncome, 2, RoundingMode.HALF_UP));
        }
        f.put("PURPOSE_CLARITY", purposeClarity(req.getPurpose()));
        if (req.getAccountCreatedAt() != null) {
            f.put("ACCOUNT_AGE_MONTHS", (int) ChronoUnit.MONTHS.between(req.getAccountCreatedAt(), LocalDateTime.now()));
        }

        // ── H · Toàn vẹn chứng từ (chống gian lận) ──
        f.put("DOCUMENT_INTEGRITY", documentIntegrity(docAnalyses));

        // Bỏ key có value null để engine flag missing thống nhất
        f.values().removeIf(Objects::isNull);
        return f;
    }

    private boolean isPositive(BigDecimal v) {
        return v != null && v.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Tính nghĩa vụ trả nợ hàng kỳ (EMI) theo dư nợ giảm dần.
     * Lãi suất null/0 → chia đều gốc cho kỳ hạn.
     */
    private BigDecimal monthlyInstallment(BigDecimal amount, BigDecimal annualRatePercent, int termMonths) {
        if (annualRatePercent == null || annualRatePercent.compareTo(BigDecimal.ZERO) <= 0) {
            return amount.divide(BigDecimal.valueOf(termMonths), 2, RoundingMode.HALF_UP);
        }
        double r = annualRatePercent.doubleValue() / 12.0 / 100.0;
        double pow = Math.pow(1 + r, termMonths);
        double emi = amount.doubleValue() * r * pow / (pow - 1);
        return BigDecimal.valueOf(emi).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * C1 — mức xác minh thu nhập: kết hợp thu nhập tự khai với kết quả AI đọc chứng từ thu nhập.
     * VERIFIED: có chứng từ thu nhập AI đánh giá nhất quán và số tiền khớp khai báo (±25%).
     * SUPPORTED: có chứng từ thu nhập (kể cả AI tắt/chưa khớp hẳn) hoặc có khai báo + chứng từ bổ trợ.
     * DECLARED_ONLY: chỉ tự khai thu nhập, chưa có chứng từ.
     * null (missing): không có thu nhập lẫn chứng từ → cần thu thập.
     */
    private String incomeVerificationLevel(BigDecimal declaredIncome,
                                           List<EvaluateScoreRequest.DocumentRef> documents,
                                           List<DocumentAnalysis> docAnalyses) {
        boolean hasIncomeDoc = documents != null && documents.stream()
                .anyMatch(d -> d.getDocType() != null && INCOME_DOC_TYPES.contains(d.getDocType()));
        boolean verifiedMatch = docAnalyses != null && docAnalyses.stream()
                .filter(d -> d.getDocType() != null && INCOME_DOC_TYPES.contains(d.getDocType()))
                .filter(d -> "CONSISTENT".equals(d.getVerdict()))
                .anyMatch(d -> incomeMatchesDeclared(d, declaredIncome));

        if (isPositive(declaredIncome)) {
            if (verifiedMatch) return "VERIFIED";
            if (hasIncomeDoc) return "SUPPORTED";
            return "DECLARED_ONLY";
        }
        // Không khai thu nhập nhưng có nộp chứng từ thu nhập
        return hasIncomeDoc ? "SUPPORTED" : null;
    }

    /** Thu nhập AI trích xuất từ extractedData JSON có khớp khai báo trong ngưỡng dung sai không. */
    private boolean incomeMatchesDeclared(DocumentAnalysis d, BigDecimal declaredIncome) {
        if (!isPositive(declaredIncome) || d.getExtractedData() == null || d.getExtractedData().isBlank()) {
            return false;
        }
        try {
            var node = objectMapper.readTree(d.getExtractedData());
            String raw = node.path("extractedMonthlyIncome").asText(null);
            if (raw == null || raw.isBlank() || "null".equalsIgnoreCase(raw)) return false;
            double extracted = Double.parseDouble(raw.replaceAll("[^0-9.]", ""));
            if (extracted <= 0) return false;
            double declared = declaredIncome.doubleValue();
            return Math.abs(extracted - declared) / declared <= INCOME_MATCH_TOLERANCE;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * E3 — chứng từ nghề nghiệp/kinh doanh (HĐLĐ, ĐKKD...).
     * CONFIRMED: AI đánh giá nhất quán; PROVIDED: đã nộp nhưng chưa xác nhận; NONE: chưa nộp.
     */
    private String occupationDocLevel(List<EvaluateScoreRequest.DocumentRef> documents,
                                      List<DocumentAnalysis> docAnalyses) {
        boolean confirmed = docAnalyses != null && docAnalyses.stream()
                .filter(d -> d.getDocType() != null && OCCUPATION_DOC_TYPES.contains(d.getDocType()))
                .anyMatch(d -> "CONSISTENT".equals(d.getVerdict()));
        if (confirmed) return "CONFIRMED";
        boolean provided = documents != null && documents.stream()
                .anyMatch(d -> d.getDocType() != null && OCCUPATION_DOC_TYPES.contains(d.getDocType()));
        return provided ? "PROVIDED" : "NONE";
    }

    /**
     * H2 — toàn vẹn chứng từ từ verdict AI trên toàn bộ file đính kèm.
     * FLAGGED nếu có HIGH_RISK; REVIEW nếu có SUSPICIOUS; CLEAN nếu tất cả ổn.
     * null (missing) khi không có chứng từ nào được AI phân tích — không kết luận được.
     */
    private String documentIntegrity(List<DocumentAnalysis> docAnalyses) {
        if (docAnalyses == null || docAnalyses.isEmpty()) return null;
        boolean anyAnalyzed = false;
        boolean anySuspicious = false;
        for (DocumentAnalysis d : docAnalyses) {
            String v = d.getVerdict();
            if (v == null) continue;
            if ("HIGH_RISK".equals(v)) return "FLAGGED";
            if ("CONSISTENT".equals(v) || "SUSPICIOUS".equals(v) || "UNREADABLE".equals(v)) anyAnalyzed = true;
            if ("SUSPICIOUS".equals(v)) anySuspicious = true;
        }
        if (!anyAnalyzed) return null;
        return anySuspicious ? "REVIEW" : "CLEAN";
    }

    /** F3 — mục đích vay rõ ràng: dựa trên độ chi tiết của trường purpose. */
    private String purposeClarity(String purpose) {
        if (purpose == null || purpose.isBlank()) return "NONE";
        return purpose.trim().length() >= 15 ? "CLEAR" : "VAGUE";
    }

    /** OccupationCategory/label tiếng Việt từ loan-service/mobile → nhóm scorecard. */
    private String mapOccupation(String occupation) {
        if (occupation == null || occupation.isBlank()) return null;
        String key = occupation.trim().toUpperCase();
        if (SCORECARD_OCCUPATIONS.contains(key)) return key;
        String mappedByCode = OCCUPATION_MAP.get(key);
        if (mappedByCode != null) return mappedByCode;
        return OCCUPATION_LABEL_MAP.getOrDefault(normalizedOccupationLabel(occupation), "OTHER");
    }

    private String normalizedOccupationLabel(String occupation) {
        String noAccent = Normalizer.normalize(occupation, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('Đ', 'D')
                .replace('đ', 'd');
        return noAccent.toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    /** raw 0..max → 300-850 */
    private int normalize(int raw, int max) {
        if (max <= 0) return 300;
        return 300 + (int) Math.round(raw * 550.0 / max);
    }

    private String gradeOf(int score) {
        if (score >= 800) return "A+";
        if (score >= 750) return "A";
        if (score >= 680) return "B";
        if (score >= 620) return "C";
        if (score >= 550) return "D";
        return "E";
    }

    private String buildAiContext(EvaluateScoreRequest req, BorrowerProfile profile,
                                  ScoringEngine.EngineResult engine, int score, String grade,
                                  List<DocumentAnalysis> docAnalyses) {
        StringBuilder sb = new StringBuilder();
        sb.append("HỒ SƠ THẨM ĐỊNH KHOẢN GỌI VỐN\n\n");

        sb.append("Khoản gọi vốn: ");
        sb.append("số tiền ").append(req.getLoanAmount() != null ? req.getLoanAmount() + " VND" : "(chưa rõ)");
        sb.append(", kỳ hạn ").append(req.getTermMonths() != null ? req.getTermMonths() + " tháng" : "(chưa rõ)");
        sb.append("\nMục đích vay: ").append(req.getPurpose() != null ? req.getPurpose() : "(không khai)").append("\n\n");

        if (profile != null) {
            sb.append("Hồ sơ tự khai: nghề nghiệp=").append(profile.getOccupationType())
                    .append(", thâm niên=").append(profile.getEmploymentYears()).append(" năm")
                    .append(", thu nhập/tháng=").append(profile.getMonthlyIncome()).append(" VND")
                    .append(", nợ hiện tại/tháng=").append(profile.getExistingMonthlyDebt()).append(" VND")
                    .append(", hôn nhân=").append(profile.getMaritalStatus())
                    .append(", người phụ thuộc=").append(profile.getDependentsCount())
                    .append(", học vấn=").append(profile.getEducationLevel()).append("\n\n");
        } else {
            sb.append("Hồ sơ tự khai: CHƯA CÓ (user chưa khai thông tin tài chính)\n\n");
        }

        sb.append("Kết quả scorecard: ").append(score).append(" điểm, hạng ").append(grade)
                .append(" (").append(engine.getTotalPoints()).append("/").append(engine.getMaxPoints()).append(" điểm thô)\n");
        sb.append("Chi tiết từng tiêu chí:\n");
        for (ScoringEngine.CriteriaResult r : engine.getDetails()) {
            sb.append("- ").append(r.getCriteriaName()).append(": ").append(r.getRawValue())
                    .append(" → ").append(r.getPoints()).append("/").append(r.getMaxPoints()).append("\n");
        }
        if (!engine.getMissingData().isEmpty()) {
            // Ô thiếu đã nhận điểm sàn → phần còn có thể nâng = maxPoints - points
            int lostToMissing = engine.getDetails().stream()
                    .filter(r -> r.getRawValue() != null && r.getRawValue().contains("thiếu dữ liệu"))
                    .mapToInt(r -> Math.max(0, r.getMaxPoints() - r.getPoints())).sum();
            sb.append("Tiêu chí thiếu dữ liệu: ").append(String.join(", ", engine.getMissingData()))
                    .append(" (mất ").append(lostToMissing).append("/").append(engine.getMaxPoints())
                    .append(" điểm thô do CHƯA CÓ dữ liệu, không phải tín hiệu xấu)\n");
            if (lostToMissing >= 0.25 * engine.getMaxPoints()) {
                sb.append("LƯU Ý QUAN TRỌNG: phần lớn điểm bị mất là do hồ sơ THIẾU DỮ LIỆU chứ chưa chắc do rủi ro. ")
                  .append("Hãy nói rõ trong summary đâu là điểm thấp do thiếu dữ liệu (có thể bổ sung để nâng hạng) ")
                  .append("và đâu là tín hiệu rủi ro thật; tránh khuyến nghị từ chối chỉ vì điểm thấp khi nguyên nhân là thiếu thông tin.\n");
            }
        }

        if (docAnalyses != null && !docAnalyses.isEmpty()) {
            sb.append("\nKẾT QUẢ AI PHÂN TÍCH CHỨNG TỪ ĐÍNH KÈM (").append(docAnalyses.size()).append(" file):\n");
            for (DocumentAnalysis d : docAnalyses) {
                sb.append("- ").append(d.getFileName() != null ? d.getFileName() : d.getFileId())
                        .append(" [").append(d.getDocType()).append("]: verdict=").append(d.getVerdict());
                if (d.getTrustScore() != null) {
                    sb.append(", độ tin cậy ").append(d.getTrustScore()).append("/100");
                }
                if (d.getSummary() != null && !d.getSummary().isBlank()) {
                    sb.append(" — ").append(d.getSummary());
                }
                sb.append("\n");
            }
            sb.append("Hãy đối chiếu kết quả chứng từ với thông tin tự khai (thu nhập, nghề nghiệp, nơi làm việc) ")
              .append("và đưa cảnh báo nếu chứng từ không nhất quán hoặc không chứng minh được thu nhập khai báo.\n");
        } else if (req.getDocuments() != null && !req.getDocuments().isEmpty()) {
            sb.append("\nLƯU Ý: Khoản gọi vốn có ").append(req.getDocuments().size())
              .append(" chứng từ đính kèm nhưng chưa phân tích được (AI tắt hoặc lỗi).\n");
        } else {
            sb.append("\nLƯU Ý: Người gọi vốn KHÔNG đính kèm chứng từ tài chính/thu nhập nào — cân nhắc rủi ro khai báo không kiểm chứng.\n");
        }
        return sb.toString();
    }

    private CreditScoreResponse toResponse(CreditScore e, List<CreditScoreDetail> details,
                                           List<String> missingData, List<String> riskFlags,
                                           List<DocumentAnalysis> docAnalyses) {
        List<CreditScoreResponse.ScoreDetailItem> items = details.stream()
                .map(d -> CreditScoreResponse.ScoreDetailItem.builder()
                        .criteriaCode(d.getCriteriaCode())
                        .criteriaName(d.getCriteriaName())
                        .component(d.getComponent())
                        .rawValue(d.getRawValue())
                        .points(d.getPoints())
                        .maxPoints(d.getMaxPoints())
                        .build())
                .toList();

        // Diễn giải nguyên nhân — deterministic, luôn có kể cả khi AI advisory null
        CreditScoreResponse.ScoreExplanation explanation = scoreExplainer.explain(
                e.getScore(), e.getGrade(),
                e.getMaxPoints() != null ? e.getMaxPoints() : 0,
                items, docAnalyses, appProperties.getAi().isEnabled());

        ReviewGate gate = computeReviewGate(items, docAnalyses);

        return CreditScoreResponse.builder()
                .id(e.getId())
                .userId(e.getUserId())
                .loanRequestId(e.getLoanRequestId())
                .score(e.getScore())
                .grade(e.getGrade())
                .gradePolicy(GRADE_POLICY.getOrDefault(e.getGrade(), ""))
                .rawPoints(e.getRawPoints())
                .maxPoints(e.getMaxPoints())
                .modelVersion(e.getModelVersion())
                .status(e.getStatus())
                .missingData(missingData)
                .details(items)
                .aiSummary(e.getAiSummary())
                .aiRiskFlags(riskFlags)
                .aiRecommendation(e.getAiRecommendation())
                .documentAnalyses(docAnalyses)
                .explanation(explanation)
                .reviewDirective(gate.directive())
                .reviewReasons(gate.reasons())
                .expiresAt(e.getExpiresAt())
                .createdAt(e.getCreatedAt())
                .build();
    }

    /**
     * Cổng loại trừ theo rule (docx §7) — chỉ tư vấn, không tự quyết định.
     * HARD_REJECT khi nợ xấu CIC (nhóm ≥3). MANUAL_REVIEW khi chứng từ rủi ro cao
     * hoặc chưa có kết quả CIC. Quyết định cuối vẫn là con người.
     */
    private ReviewGate computeReviewGate(List<CreditScoreResponse.ScoreDetailItem> items,
                                         List<DocumentAnalysis> docAnalyses) {
        List<String> reasons = new ArrayList<>();
        String directive = "AUTO";

        // CIC — nợ xấu hoặc chưa tra
        CreditScoreResponse.ScoreDetailItem cic = items.stream()
                .filter(i -> "CIC_DEBT_GROUP".equals(i.getCriteriaCode()))
                .findFirst().orElse(null);
        if (cic != null) {
            Integer group = parseLeadingInt(cic.getRawValue());
            if (group == null) {
                directive = escalate(directive, "MANUAL_REVIEW");
                reasons.add("Chưa có kết quả CIC — cần tra cứu CIC bên ngoài và nhập trước khi quyết định.");
            } else if (group >= 3) {
                directive = escalate(directive, "HARD_REJECT");
                reasons.add("Nợ xấu CIC nhóm " + group + " — theo chính sách phải từ chối hoặc đưa rà soát đặc biệt.");
            }
        }

        // Chứng từ rủi ro cao
        boolean docHighRisk = docAnalyses != null && docAnalyses.stream()
                .anyMatch(d -> "HIGH_RISK".equals(d.getVerdict()));
        if (docHighRisk) {
            directive = escalate(directive, "MANUAL_REVIEW");
            reasons.add("Có chứng từ AI đánh dấu rủi ro cao — phải kiểm tra thủ công bản gốc.");
        }

        return new ReviewGate(directive, reasons);
    }

    /** Lấy số nguyên đứng đầu chuỗi rawValue (vd "3" hoặc "Nhóm 3..."); null nếu thiếu dữ liệu. */
    private Integer parseLeadingInt(String raw) {
        if (raw == null || raw.contains("thiếu dữ liệu")) return null;
        var m = java.util.regex.Pattern.compile("\\d+").matcher(raw);
        return m.find() ? Integer.parseInt(m.group()) : null;
    }

    private static final List<String> GATE_SEVERITY = List.of("AUTO", "MANUAL_REVIEW", "HARD_REJECT");

    private String escalate(String current, String candidate) {
        return GATE_SEVERITY.indexOf(candidate) > GATE_SEVERITY.indexOf(current) ? candidate : current;
    }

    private record ReviewGate(String directive, List<String> reasons) {}

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.error("JSON serialize error: {}", e.getMessage());
            return null;
        }
    }

    private List<String> fromJsonList(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return null;
        }
    }
}
