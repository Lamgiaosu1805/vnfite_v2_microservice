package com.p2plending.credit.service.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Demo AI risk assessor — sinh advisory text thực tế dựa trên grade/context,
 * không gọi API nào. Dùng để test UI khi chưa có ANTHROPIC_API_KEY.
 *
 * Bật bằng: APP_AI_ENABLED=true + APP_AI_MODE=demo
 */
@Service
@ConditionalOnProperty(prefix = "app.ai", name = "enabled", havingValue = "true")
@ConditionalOnExpression("'${app.ai.mode:demo}'.equals('demo')")
@Slf4j
public class DemoAiRiskAssessor implements AiRiskAssessor {

    private static final Pattern GRADE_PATTERN   = Pattern.compile("hạng\\s+([A-E])", Pattern.CASE_INSENSITIVE);
    private static final Pattern SCORE_PATTERN   = Pattern.compile("(\\d{3})\\s+điểm");
    private static final Pattern MISSING_PATTERN = Pattern.compile("Tiêu chí thiếu dữ liệu:\\s+(.+)");
    private static final Pattern PROFILE_MISSING = Pattern.compile("CHƯA CÓ");

    public DemoAiRiskAssessor() {
        log.info("DemoAiRiskAssessor bật — không gọi AI API (chỉ dùng để test UI)");
    }

    @Override
    public AiRiskAssessment assess(String context) {
        String grade    = extractGrade(context);
        int    score    = extractScore(context);
        boolean noProfile  = PROFILE_MISSING.matcher(context).find();
        List<String> missing = extractMissingCriteria(context);

        String summary        = buildSummary(grade, score, noProfile, missing);
        List<String> flags    = buildFlags(grade, noProfile, missing, context);
        String recommendation = buildRecommendation(grade, noProfile, missing);

        log.debug("[DEMO] AI advisory generated — grade={} score={} flags={}", grade, score, flags.size());
        return new AiRiskAssessment(summary, flags, recommendation);
    }

    // ── Extractors ────────────────────────────────────────────────────────────

    private String extractGrade(String context) {
        Matcher m = GRADE_PATTERN.matcher(context);
        return m.find() ? m.group(1).toUpperCase() : "C";
    }

    private int extractScore(String context) {
        Matcher m = SCORE_PATTERN.matcher(context);
        return m.find() ? Integer.parseInt(m.group(1)) : 500;
    }

    private List<String> extractMissingCriteria(String context) {
        Matcher m = MISSING_PATTERN.matcher(context);
        if (!m.find()) return List.of();
        String raw = m.group(1).trim();
        List<String> items = new ArrayList<>();
        for (String s : raw.split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) items.add(t);
        }
        return items;
    }

    // ── Advisory builders ────────────────────────────────────────────────────

    private String buildSummary(String grade, int score, boolean noProfile, List<String> missing) {
        return switch (grade) {
            case "A" -> "Hồ sơ có chất lượng tín dụng tốt với điểm " + score +
                    ". Lịch sử nền tảng và thông tin khai báo nhất quán, không phát hiện dấu hiệu bất thường." +
                    (missing.isEmpty() ? " Hồ sơ đầy đủ thông tin để thẩm định." :
                            " Tuy nhiên vẫn còn " + missing.size() + " tiêu chí thiếu dữ liệu cần bổ sung.");
            case "B" -> "Hồ sơ đạt mức tín dụng khá với điểm " + score +
                    ". Thông tin cơ bản đầy đủ, một số tiêu chí có thể cải thiện thêm." +
                    (noProfile ? " Người gọi vốn chưa khai hồ sơ tài chính chi tiết." : "");
            case "C" -> "Hồ sơ ở mức trung bình với điểm " + score +
                    ". Có một số điểm cần xem xét kỹ trước khi quyết định." +
                    (noProfile ? " Người gọi vốn chưa cung cấp thông tin tài chính tự khai." :
                            " Nên yêu cầu bổ sung chứng từ để làm rõ.");
            case "D" -> "Hồ sơ ở mức yếu với điểm " + score +
                    ". Nhiều tiêu chí chấm điểm thấp, cần thẩm định chặt chẽ trước khi xét duyệt." +
                    (missing.size() > 2 ? " Thiếu nhiều dữ liệu quan trọng ảnh hưởng đến độ tin cậy của điểm số." : "");
            default  -> "Hồ sơ có rủi ro cao với điểm " + score +
                    ". Điểm số phản ánh nhiều hạn chế về năng lực tài chính và lịch sử tín dụng." +
                    " Cần thẩm định rất thận trọng hoặc yêu cầu bảo lãnh bổ sung.";
        };
    }

    private List<String> buildFlags(String grade, boolean noProfile, List<String> missing, String context) {
        List<String> flags = new ArrayList<>();

        if (noProfile) {
            flags.add("Chưa có hồ sơ tài chính tự khai — điểm thu nhập/nợ bằng 0");
        }
        if (missing.size() >= 3) {
            flags.add("Thiếu " + missing.size() + " tiêu chí dữ liệu: " + String.join(", ", missing));
        } else {
            for (String m : missing) flags.add("Thiếu dữ liệu: " + m);
        }

        if (context.contains("(chưa rõ)") || context.contains("(không khai)")) {
            flags.add("Mục đích vay hoặc số tiền chưa được khai báo rõ ràng");
        }

        if ("D".equals(grade) || "E".equals(grade)) {
            flags.add("Điểm tín dụng thấp — cần kiểm tra nguồn thu nhập thực tế");
            if (!noProfile && context.contains("nợ hiện tại")) {
                flags.add("Tỷ lệ nợ/thu nhập cần được xác minh qua sao kê ngân hàng");
            }
        }

        return flags;
    }

    private String buildRecommendation(String grade, boolean noProfile, List<String> missing) {
        if ("A".equals(grade) || "B".equals(grade)) {
            return noProfile
                    ? "Yêu cầu bổ sung hồ sơ tài chính tự khai để hoàn thiện hồ sơ trước khi duyệt."
                    : "Có thể xem xét phê duyệt; nên xác minh nhanh thu nhập qua sao kê 3 tháng gần nhất.";
        }
        if ("C".equals(grade)) {
            return "Yêu cầu bổ sung sao kê lương hoặc sao kê ngân hàng 3 tháng gần nhất. "
                    + (missing.isEmpty() ? "Xem xét kỹ mục đích sử dụng vốn." :
                            "Cần điền đủ thông tin còn thiếu trước khi thẩm định tiếp.");
        }
        return "Cần thẩm định trực tiếp; yêu cầu đầy đủ chứng từ thu nhập, hợp đồng lao động, "
                + "và xác minh nơi làm việc trước khi xem xét duyệt.";
    }
}
