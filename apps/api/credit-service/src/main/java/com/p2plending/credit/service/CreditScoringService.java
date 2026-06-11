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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
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

    private static final Set<String> SCORECARD_OCCUPATIONS =
            Set.of("GOV_EMPLOYEE", "SALARIED", "BUSINESS_OWNER", "FREELANCER", "OTHER");

    private static final Map<String, String> GRADE_POLICY = Map.of(
            "A", "Rủi ro thấp — đề xuất duyệt nhanh, lãi suất ưu đãi",
            "B", "Rủi ro khá thấp — đề xuất duyệt, điều kiện chuẩn",
            "C", "Rủi ro trung bình — thẩm định kỹ, cân nhắc giảm hạn mức",
            "D", "Rủi ro cao — yêu cầu bổ sung hồ sơ/chứng từ thu nhập, giảm hạn mức",
            "E", "Rủi ro rất cao — đề xuất từ chối"
    );

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

        Map<String, Object> features = buildFeatures(req, profile);
        ScoringEngine.EngineResult engine = scoringEngine.evaluate(features);

        int score = normalize(engine.getTotalPoints(), engine.getMaxPoints());
        String grade = gradeOf(score);

        // AI phân tích toàn bộ chứng từ trước — kết quả đưa vào advisory để đánh giá tổng thể.
        // Fail từng file không chặn chấm điểm.
        List<DocumentAnalysis> docAnalyses = List.of();
        try {
            docAnalyses = documentAnalysisService.analyzeForScoring(req);
        } catch (Exception e) {
            log.error("AI document analysis error (bỏ qua): {}", e.getMessage());
        }

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

    private Map<String, Object> buildFeatures(EvaluateScoreRequest req, BorrowerProfile profile) {
        Map<String, Object> f = new LinkedHashMap<>();

        if (req.getDateOfBirth() != null) {
            f.put("AGE", Period.between(req.getDateOfBirth(), LocalDate.now()).getYears());
        }

        // Thu nhập/nghề nghiệp/nợ: ưu tiên dữ liệu từ chính đơn gọi vốn, fallback borrower_profiles
        BigDecimal monthlyIncome = req.getMonthlyIncome() != null ? req.getMonthlyIncome()
                : (profile != null ? profile.getMonthlyIncome() : null);
        String occupation = req.getOccupation() != null ? req.getOccupation()
                : (profile != null ? profile.getOccupationType() : null);
        BigDecimal existingDebt = req.getExistingMonthlyDebt() != null ? req.getExistingMonthlyDebt()
                : (profile != null ? profile.getExistingMonthlyDebt() : null);

        f.put("MONTHLY_INCOME", monthlyIncome);
        f.put("OCCUPATION_TYPE", mapOccupation(occupation));

        if (profile != null) {
            f.put("MARITAL_STATUS", profile.getMaritalStatus());
            f.put("DEPENDENTS", profile.getDependentsCount());
            f.put("EDUCATION_LEVEL", profile.getEducationLevel());
            f.put("EMPLOYMENT_YEARS", profile.getEmploymentYears());
        }

        if (isPositive(monthlyIncome) && existingDebt != null) {
            f.put("DTI_RATIO", existingDebt
                    .multiply(BigDecimal.valueOf(100))
                    .divide(monthlyIncome, 2, RoundingMode.HALF_UP));
        }
        if (isPositive(monthlyIncome) && req.getLoanAmount() != null) {
            BigDecimal annualIncome = monthlyIncome.multiply(BigDecimal.valueOf(12));
            f.put("LOAN_TO_ANNUAL_INCOME", req.getLoanAmount()
                    .multiply(BigDecimal.valueOf(100))
                    .divide(annualIncome, 2, RoundingMode.HALF_UP));
        }
        f.put("COMPLETED_LOANS", req.getCompletedLoanCount());
        if (req.getAccountCreatedAt() != null) {
            f.put("ACCOUNT_AGE_MONTHS", (int) ChronoUnit.MONTHS.between(req.getAccountCreatedAt(), LocalDateTime.now()));
        }
        f.put("KYC_STATUS", req.getKycStatus());
        if (req.getHasReferrer() != null) {
            f.put("HAS_REFERRER", req.getHasReferrer() ? "YES" : "NO");
        }

        // Bỏ key có value null để engine flag missing thống nhất
        f.values().removeIf(Objects::isNull);
        return f;
    }

    private boolean isPositive(BigDecimal v) {
        return v != null && v.compareTo(BigDecimal.ZERO) > 0;
    }

    /** OccupationCategory loan-service → nhóm scorecard; không nhận diện được → OTHER (conservative) */
    private String mapOccupation(String occupation) {
        if (occupation == null || occupation.isBlank()) return null;
        String key = occupation.trim().toUpperCase();
        if (SCORECARD_OCCUPATIONS.contains(key)) return key;
        return OCCUPATION_MAP.getOrDefault(key, "OTHER");
    }

    /** raw 0..max → 300-850 */
    private int normalize(int raw, int max) {
        if (max <= 0) return 300;
        return 300 + (int) Math.round(raw * 550.0 / max);
    }

    private String gradeOf(int score) {
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
            sb.append("Tiêu chí thiếu dữ liệu: ").append(String.join(", ", engine.getMissingData())).append("\n");
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
            sb.append("\nLƯU Ý: Người gọi vốn KHÔNG đính kèm chứng từ thu nhập nào — cân nhắc rủi ro khai báo không kiểm chứng.\n");
        }
        return sb.toString();
    }

    private CreditScoreResponse toResponse(CreditScore e, List<CreditScoreDetail> details,
                                           List<String> missingData, List<String> riskFlags,
                                           List<DocumentAnalysis> docAnalyses) {
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
                .details(details.stream().map(d -> CreditScoreResponse.ScoreDetailItem.builder()
                        .criteriaCode(d.getCriteriaCode())
                        .criteriaName(d.getCriteriaName())
                        .component(d.getComponent())
                        .rawValue(d.getRawValue())
                        .points(d.getPoints())
                        .maxPoints(d.getMaxPoints())
                        .build()).toList())
                .aiSummary(e.getAiSummary())
                .aiRiskFlags(riskFlags)
                .aiRecommendation(e.getAiRecommendation())
                .documentAnalyses(docAnalyses)
                .expiresAt(e.getExpiresAt())
                .createdAt(e.getCreatedAt())
                .build();
    }

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
