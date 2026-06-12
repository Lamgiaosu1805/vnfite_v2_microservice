package com.p2plending.credit.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2plending.credit.domain.entity.DocumentAnalysis;
import com.p2plending.credit.dto.response.CreditScoreResponse.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Sinh diễn giải "tại sao" cho điểm tín dụng — HOÀN TOÀN deterministic, không gọi AI.
 *
 * <p>Mục tiêu: thẩm định viên nhìn vào là hiểu vì sao có hạng này, đặc biệt phân biệt
 * <b>điểm thấp do hồ sơ thiếu dữ liệu</b> (có thể nâng hạng bằng cách thu thập thêm)
 * với <b>điểm thấp do tín hiệu rủi ro thật</b> (đã có dữ liệu nhưng rơi nhóm thấp).
 *
 * <p>Luôn chạy được kể cả khi AI advisory (Gemini/Claude) tắt hoặc lỗi.
 */
@Service
@Slf4j
public class ScoreExplainer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Tỷ lệ điểm tối thiểu coi là "điểm mạnh". */
    private static final double STRENGTH_RATIO = 0.8;
    private static final int MAX_DRIVERS = 6;
    private static final String MISSING_MARK = "thiếu dữ liệu";

    /** Hướng dẫn thu thập dữ liệu còn thiếu theo từng tiêu chí (khung Credit Score 360). */
    private static final Map<String, String> HOW_TO_OBTAIN = Map.ofEntries(
            Map.entry("INCOME_VERIFICATION", "Yêu cầu khai thu nhập và nộp chứng từ: sao kê lương/ngân hàng, bảng lương, doanh thu/hóa đơn/sổ bán hàng hoặc chứng từ thu nhập khác"),
            Map.entry("PTI_RATIO", "Cần thu nhập tự khai + số tiền & kỳ hạn khoản vay để tính tỷ lệ trả nợ kỳ/thu nhập"),
            Map.entry("DTI_RATIO", "Yêu cầu khai thu nhập và nợ hiện tại hàng tháng để tính tổng nợ/thu nhập"),
            Map.entry("LOAN_TO_ANNUAL_INCOME", "Cần thu nhập tự khai để tính khoản vay/thu nhập năm"),
            Map.entry("EMPLOYMENT_YEARS", "Yêu cầu khai thâm niên công tác/kinh doanh"),
            Map.entry("OCCUPATION_TYPE", "Yêu cầu khai loại nghề nghiệp trong hồ sơ tài chính"),
            Map.entry("OCCUPATION_DOC", "Yêu cầu nộp hợp đồng lao động hoặc giấy phép kinh doanh"),
            Map.entry("DOCUMENT_INTEGRITY", "Cần bật AI thẩm định và có chứng từ để kiểm tra tính toàn vẹn"),
            Map.entry("COMPLETED_LOANS", "Lịch sử vay trên VNFITE — tích lũy theo thời gian"),
            Map.entry("ACCOUNT_AGE_MONTHS", "Tài khoản còn mới — cải thiện theo thời gian sử dụng"),
            Map.entry("KYC_STATUS", "Hoàn tất định danh eKYC"),
            Map.entry("CIC_DEBT_GROUP", "Tra cứu CIC bên ngoài và nhập nhóm nợ vào CMS (chờ API NĐ94)"),
            Map.entry("CIC_MAX_DPD", "Tra cứu CIC bên ngoài và nhập số ngày quá hạn cao nhất"),
            Map.entry("CIC_ACTIVE_LENDERS", "Tra cứu CIC bên ngoài và nhập số tổ chức đang có dư nợ")
    );

    public ScoreExplanation explain(int score, String grade, int maxPoints,
                                    List<ScoreDetailItem> details,
                                    List<DocumentAnalysis> docAnalyses,
                                    boolean aiEnabled) {
        List<Driver> negative = new ArrayList<>();
        List<Driver> positive = new ArrayList<>();
        List<MissingItem> missing = new ArrayList<>();

        int lostToMissing = 0;
        int lostToWeak = 0;
        int withData = 0;
        int total = details != null ? details.size() : 0;

        if (details != null) {
            for (ScoreDetailItem d : details) {
                int pts = d.getPoints() != null ? d.getPoints() : 0;
                int max = d.getMaxPoints() != null ? d.getMaxPoints() : 0;
                if (max <= 0) continue;

                if (isMissing(d)) {
                    lostToMissing += max;
                    missing.add(MissingItem.builder()
                            .criteriaName(d.getCriteriaName())
                            .potentialPoints(max)
                            .howToObtain(HOW_TO_OBTAIN.getOrDefault(d.getCriteriaCode(),
                                    "Bổ sung dữ liệu tiêu chí này trong hồ sơ"))
                            .build());
                    continue;
                }

                withData++;
                double ratio = (double) pts / max;
                if (ratio >= STRENGTH_RATIO) {
                    positive.add(Driver.builder()
                            .criteriaName(d.getCriteriaName())
                            .component(d.getComponent())
                            .points(pts).maxPoints(max)
                            .reason(strengthReason(pts, max))
                            .build());
                } else {
                    lostToWeak += (max - pts);
                    negative.add(Driver.builder()
                            .criteriaName(d.getCriteriaName())
                            .component(d.getComponent())
                            .points(pts).maxPoints(max)
                            .reason(weakReason(pts, max, d.getRawValue()))
                            .build());
                }
            }
        }

        // Yếu tố kéo điểm xuống mạnh nhất / điểm mạnh lớn nhất lên đầu
        negative.sort(Comparator.comparingInt((Driver x) ->
                (x.getMaxPoints() - x.getPoints())).reversed());
        positive.sort(Comparator.comparingInt(Driver::getPoints).reversed());
        missing.sort(Comparator.comparingInt(MissingItem::getPotentialPoints).reversed());
        trim(negative);
        trim(positive);

        int uplift = maxPoints > 0 ? (int) Math.round(lostToMissing * 550.0 / maxPoints) : 0;

        DocumentInsight docInsight = buildDocumentInsight(docAnalyses, aiEnabled);

        boolean thinFile = lostToMissing >= lostToWeak
                && maxPoints > 0 && lostToMissing >= 0.25 * maxPoints;

        String headline = buildHeadline(grade, thinFile, lostToMissing, uplift, negative);
        String action = buildSuggestedAction(grade, thinFile, missing, docInsight);

        return ScoreExplanation.builder()
                .headline(headline)
                .suggestedAction(action)
                .criteriaWithData(withData)
                .criteriaTotal(total)
                .pointsLostToMissingData(lostToMissing)
                .pointsLostToWeakSignals(lostToWeak)
                .maxPotentialScoreUplift(uplift)
                .negativeDrivers(negative)
                .positiveDrivers(positive)
                .missingData(missing)
                .documents(docInsight)
                .build();
    }

    // ── Headline & action ──────────────────────────────────────────────────────

    private String buildHeadline(String grade, boolean thinFile, int lostToMissing,
                                 int uplift, List<Driver> negative) {
        if ("A+".equals(grade) || "A".equals(grade) || "B".equals(grade)) {
            return "Hồ sơ tốt — các chỉ tiêu chính đều đạt. Tập trung xác minh chứng từ trước khi trình.";
        }
        if (thinFile) {
            return "Điểm thấp chủ yếu do hồ sơ thiếu dữ liệu (chưa chấm được " + lostToMissing
                    + " điểm thô, tương đương tối đa ~" + uplift + " điểm tín dụng), chưa phải tín hiệu rủi ro xấu. "
                    + "Thu thập thêm thông tin có thể nâng hạng đáng kể — không nên từ chối chỉ vì điểm thấp.";
        }
        String top = negative.stream().limit(3)
                .map(Driver::getCriteriaName)
                .reduce((a, b) -> a + ", " + b).orElse("một số tiêu chí");
        return "Điểm thấp do tín hiệu rủi ro thực ở: " + top + ". Cần thẩm định kỹ trước khi quyết định.";
    }

    private String buildSuggestedAction(String grade, boolean thinFile,
                                        List<MissingItem> missing, DocumentInsight docs) {
        StringBuilder sb = new StringBuilder();
        if (thinFile && !missing.isEmpty()) {
            String top = missing.stream().limit(3)
                    .map(MissingItem::getCriteriaName)
                    .reduce((a, b) -> a + ", " + b).orElse("");
            sb.append("Bổ sung/xác minh: ").append(top).append(". ");
        }
        if (docs != null && docs.getTotal() != null && docs.getTotal() > 0) {
            int flagged = nz(docs.getSuspicious()) + nz(docs.getHighRisk()) + nz(docs.getErrored());
            if (flagged > 0) {
                sb.append("Kiểm tra thủ công ").append(flagged).append(" chứng từ AI đánh dấu nghi vấn. ");
            } else {
                sb.append("Chứng từ AI đánh giá nhất quán — vẫn nên đối chiếu nhanh bản gốc. ");
            }
        } else {
            sb.append("Chưa có chứng từ tài chính — yêu cầu người gọi vốn nộp chứng từ phù hợp với nguồn thu nhập để đối chiếu. ");
        }
        if ("A+".equals(grade) || "A".equals(grade) || "B".equals(grade)) {
            sb.append("Nếu chứng từ khớp, có thể đề xuất duyệt theo điều kiện chuẩn.");
        } else if ("E".equals(grade) && thinFile) {
            sb.append("Không nên kết luận từ chối khi hồ sơ còn thiếu dữ liệu.");
        } else {
            sb.append("Cân nhắc giảm hạn mức hoặc yêu cầu bổ sung tài sản đảm bảo/người bảo lãnh.");
        }
        return sb.toString().trim();
    }

    // ── Reason text ─────────────────────────────────────────────────────────────

    private String strengthReason(int pts, int max) {
        return pts >= max ? "Đạt tối đa (" + max + " điểm)."
                : "Đạt " + pts + "/" + max + " — nhóm điểm cao.";
    }

    private String weakReason(int pts, int max, String rawValue) {
        double ratio = (double) pts / max;
        String tail = (rawValue != null && !rawValue.isBlank() && !rawValue.contains(MISSING_MARK))
                ? " (giá trị: " + rawValue + ")" : "";
        if (pts == 0) {
            return "0/" + max + " — rơi vào nhóm thấp nhất của tiêu chí này" + tail + ".";
        }
        if (ratio >= 0.6) {
            return "Đạt " + pts + "/" + max + " — khá nhưng còn dư địa cải thiện" + tail + ".";
        }
        if (ratio >= 0.3) {
            return "Chỉ đạt " + pts + "/" + max + " — dưới trung bình" + tail + ".";
        }
        return "Đạt " + pts + "/" + max + " — nhóm điểm thấp" + tail + ".";
    }

    // ── Document insight ────────────────────────────────────────────────────────

    private DocumentInsight buildDocumentInsight(List<DocumentAnalysis> docs, boolean aiEnabled) {
        int total = docs != null ? docs.size() : 0;
        int consistent = 0, suspicious = 0, highRisk = 0, unreadable = 0, errored = 0;
        List<String> alerts = new ArrayList<>();

        if (docs != null) {
            for (DocumentAnalysis d : docs) {
                String v = d.getVerdict() != null ? d.getVerdict() : "";
                switch (v) {
                    case "CONSISTENT" -> consistent++;
                    case "SUSPICIOUS" -> { suspicious++; collectAlerts(d, alerts); }
                    case "HIGH_RISK" -> { highRisk++; collectAlerts(d, alerts); }
                    case "UNREADABLE" -> unreadable++;
                    case "ERROR" -> errored++;
                    default -> { }
                }
            }
        }

        String summary;
        if (!aiEnabled) {
            summary = "AI thẩm định chứng từ đang tắt — cần đối chiếu chứng từ thủ công.";
        } else if (total == 0) {
            summary = "Người gọi vốn chưa đính kèm chứng từ tài chính/thu nhập nào.";
        } else {
            int flagged = suspicious + highRisk;
            summary = total + " chứng từ · " + consistent + " nhất quán"
                    + (flagged > 0 ? " · " + flagged + " nghi vấn" : "")
                    + (unreadable > 0 ? " · " + unreadable + " không đọc được" : "")
                    + (errored > 0 ? " · " + errored + " lỗi phân tích" : "");
        }

        return DocumentInsight.builder()
                .aiEnabled(aiEnabled)
                .total(total)
                .consistent(consistent)
                .suspicious(suspicious)
                .highRisk(highRisk)
                .unreadable(unreadable)
                .errored(errored)
                .alerts(alerts)
                .summary(summary)
                .build();
    }

    /** Bóc consistencyIssues/findings từ extractedData JSON để gộp cảnh báo. */
    private void collectAlerts(DocumentAnalysis d, List<String> alerts) {
        String label = d.getFileName() != null ? d.getFileName()
                : (d.getDocType() != null ? d.getDocType() : "Chứng từ");
        if (d.getExtractedData() == null || d.getExtractedData().isBlank()) {
            if (d.getSummary() != null && !d.getSummary().isBlank()) {
                alerts.add(label + ": " + d.getSummary());
            }
            return;
        }
        try {
            JsonNode node = MAPPER.readTree(d.getExtractedData());
            boolean added = false;
            for (JsonNode issue : node.path("consistencyIssues")) {
                String v = issue.asText("").trim();
                if (!v.isEmpty()) { alerts.add(label + ": " + v); added = true; }
            }
            if (!added) {
                for (JsonNode f : node.path("findings")) {
                    String v = f.asText("").trim();
                    if (!v.isEmpty()) { alerts.add(label + ": " + v); break; }
                }
            }
        } catch (Exception e) {
            log.debug("Không đọc được extractedData để gộp cảnh báo: {}", e.getMessage());
        }
    }

    // ── utils ───────────────────────────────────────────────────────────────────

    private boolean isMissing(ScoreDetailItem d) {
        return (d.getPoints() == null || d.getPoints() == 0)
                && d.getRawValue() != null && d.getRawValue().contains(MISSING_MARK);
    }

    private void trim(List<Driver> list) {
        while (list.size() > MAX_DRIVERS) list.remove(list.size() - 1);
    }

    private int nz(Integer v) {
        return v != null ? v : 0;
    }
}
