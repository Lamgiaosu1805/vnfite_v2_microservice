package com.p2plending.credit.service.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.p2plending.credit.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Dùng Gemini Flash Vision để phân tích chứng từ tài chính/thu nhập (ảnh + PDF).
 * Bật bằng: APP_AI_ENABLED=true + APP_AI_MODE=gemini
 *
 * Kết quả là CẢNH BÁO mức độ tin cậy để hỗ trợ admin — không phải phán quyết.
 * AI không thể khẳng định 100% chứng từ là giả.
 */
@Service
@ConditionalOnProperty(prefix = "app.ai", name = "enabled", havingValue = "true")
@ConditionalOnExpression("'${app.ai.mode:demo}'.equals('gemini')")
@Slf4j
public class GeminiAiDocumentAnalyzer implements AiDocumentAnalyzer {

    private static final ObjectMapper LENIENT_JSON = JsonMapper.builder()
            .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
            .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
            .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
            .build();

    private static final String SCHEMA = """
            Trả về JSON với cấu trúc sau (không có text nào ngoài JSON):
            {
              "docTypeDetected": "loại chứng từ AI nhận diện được (vd: Sao kê lương MB Bank, Hợp đồng lao động, Sổ bán hàng, Hóa đơn, Sao kê ví/POS)",
              "verdict": "CONSISTENT hoặc SUSPICIOUS hoặc HIGH_RISK hoặc UNREADABLE",
              "trustScore": <số nguyên 0-100, 100 = hoàn toàn nhất quán>,
              "ownerName": "tên chủ tài khoản/người lao động trích xuất, hoặc null",
              "organizationName": "tên ngân hàng/công ty phát hành, hoặc null",
              "extractedMonthlyIncome": "thu nhập hàng tháng dạng số (VND), hoặc null",
              "findings": ["phát hiện 1", "phát hiện 2"],
              "consistencyIssues": ["điểm không khớp với khai báo 1", ...],
              "summary": "tóm tắt 2-3 câu cho admin thẩm định bằng tiếng Việt"
            }
            verdict:
            - CONSISTENT: không phát hiện bất thường
            - SUSPICIOUS: có điểm đáng ngờ cần kiểm tra thêm
            - HIGH_RISK: nhiều dấu hiệu bất thường nghiêm trọng
            - UNREADABLE: không đọc được nội dung
            """;

    private static final String SYSTEM_INSTRUCTION = """
            Bạn là chuyên gia thẩm định chứng từ tài chính của nền tảng cho vay ngang hàng VNFITE tại Việt Nam.
            Đây là quy trình phòng chống gian lận hợp pháp: người gọi vốn tự nguyện nộp chứng từ để chứng minh thu nhập.
            Người gọi vốn có thể là người hưởng lương cố định, hộ kinh doanh, tiểu thương, lao động tự do hoặc chủ doanh nghiệp nhỏ.
            Không mặc định mọi hồ sơ phải có sao kê lương; hãy đánh giá chứng từ theo loại nguồn thu phù hợp.

            Quy trình phân tích 4 bước:
            1. TRÍCH XUẤT: loại chứng từ, tên người/tổ chức, thu nhập/doanh thu/số dư
            2. KIỂM TRA NỘI TẠI: số dư chạy có khớp không, định dạng ngày/số, font chữ đồng nhất
            3. ĐỐI CHIẾU: thông tin trích xuất có khớp với thông tin khai báo không
            4. ĐÁNH GIÁ: mức độ tin cậy tổng thể

            Tuyệt đối không phán quyết giả/thật — chỉ đánh giá mức độ tin cậy và nêu các điểm cần xác minh thêm.
            """;

    private final GeminiClient gemini;
    private final ObjectMapper objectMapper;

    public GeminiAiDocumentAnalyzer(
            AppProperties props,
            @Qualifier("geminiRestTemplate") RestTemplate restTemplate,
            ObjectMapper objectMapper
    ) {
        validateGeminiConfig(props);
        this.gemini       = new GeminiClient(restTemplate, objectMapper,
                props.getAi().getGeminiApiKey().trim(), props.getAi().getGeminiModel());
        this.objectMapper = objectMapper;
        log.info("GeminiAiDocumentAnalyzer bật — model={}", props.getAi().getGeminiModel());
    }

    @Override
    public DocumentCheckResult analyze(String mimeType, String fileBase64, String context) {
        String prompt = SYSTEM_INSTRUCTION + "\n\n" + context + "\n\n" + SCHEMA;

        try {
            List<GeminiClient.Part> parts = List.of(
                    GeminiClient.Part.file(mimeType, fileBase64),
                    GeminiClient.Part.text(prompt)
            );

            String json = gemini.generateContent(parts);
            if (json == null || json.isBlank()) {
                log.warn("Gemini trả về rỗng cho document analysis");
                return fallbackUnreadable("Gemini không trả về kết quả phân tích.");
            }

            return parseResultWithRepair(json);

        } catch (Exception e) {
            log.error("Gemini document analysis failed: {}", e.getMessage(), e);
            return fallbackUnreadable("Gemini trả về kết quả không hợp lệ, cần bấm phân tích lại hoặc thẩm định thủ công.");
        }
    }

    @Override
    public List<DocumentCheckResult> analyzePages(List<DocumentInput> documents, String context) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }

        String prompt = SYSTEM_INSTRUCTION
                + "\n\n" + context
                + "\n\nCác file ảnh/PDF đính kèm là CÙNG MỘT bộ chứng từ nhiều trang. "
                + "Hãy phân tích tổng thể tất cả trang trong một lần. Nếu thông tin nằm ở trang khác thì vẫn coi là có trong bộ chứng từ; "
                + "không đưa các câu như 'không có trên trang này' vào consistencyIssues. "
                + "Chỉ đưa vào consistencyIssues khi toàn bộ bộ chứng từ không khớp với thông tin khai báo."
                + "\n\n" + SCHEMA;

        try {
            List<GeminiClient.Part> parts = new ArrayList<>();
            for (DocumentInput document : documents) {
                parts.add(GeminiClient.Part.file(document.mimeType(), document.fileBase64()));
            }
            parts.add(GeminiClient.Part.text(prompt));

            String json = gemini.generateContent(parts);
            if (json == null || json.isBlank()) {
                log.warn("Gemini trả về rỗng cho multi-page document analysis");
                return List.of(fallbackUnreadable("Gemini không trả về kết quả phân tích."));
            }
            return List.of(parseResultWithRepair(json));
        } catch (Exception e) {
            log.error("Gemini multi-page document analysis failed: {}", e.getMessage(), e);
            return List.of(fallbackUnreadable("Gemini trả về kết quả không hợp lệ, cần bấm phân tích lại hoặc thẩm định thủ công."));
        }
    }

    private DocumentCheckResult parseResultWithRepair(String json) throws Exception {
        try {
            return parseResult(json);
        } catch (JsonProcessingException ex) {
            log.warn("Gemini trả về JSON không hợp lệ, thử yêu cầu sửa JSON. error={} rawPreview={}",
                    ex.getOriginalMessage(), preview(json));
            String repaired = repairJsonResponse(json);
            if (repaired != null && !repaired.isBlank()) {
                try {
                    return parseResult(repaired);
                } catch (JsonProcessingException repairEx) {
                    log.warn("Gemini sửa JSON vẫn không hợp lệ. error={} repairedPreview={}",
                            repairEx.getOriginalMessage(), preview(repaired));
                }
            }
            return fallbackUnreadable("Gemini trả về JSON không hợp lệ. Vui lòng bấm phân tích lại.");
        }
    }

    private DocumentCheckResult parseResult(String json) throws Exception {
        // Gemini đôi khi bọc JSON trong markdown code block — strip nếu có
        String clean = extractJsonObject(json.trim());
        if (clean.startsWith("```")) {
            clean = clean.replaceAll("^```[a-z]*\\n?", "").replaceAll("```$", "").trim();
        }

        JsonNode node = LENIENT_JSON.readTree(clean);

        String verdict = node.path("verdict").asText("UNREADABLE").toUpperCase();
        if (!List.of("CONSISTENT", "SUSPICIOUS", "HIGH_RISK", "UNREADABLE").contains(verdict)) {
            verdict = "UNREADABLE";
        }

        List<String> findings = parseStringArray(node.path("findings"));
        List<String> issues   = parseStringArray(node.path("consistencyIssues"));

        String incomeRaw = node.path("extractedMonthlyIncome").asText(null);
        if ("null".equalsIgnoreCase(incomeRaw) || (incomeRaw != null && incomeRaw.isBlank())) {
            incomeRaw = null;
        }

        return new DocumentCheckResult(
                nullIfBlank(node.path("docTypeDetected").asText(null)),
                verdict,
                node.path("trustScore").asInt(50),
                nullIfBlank(node.path("ownerName").asText(null)),
                nullIfBlank(node.path("organizationName").asText(null)),
                incomeRaw,
                findings,
                issues,
                node.path("summary").asText("Không có tóm tắt.")
        );
    }

    private String repairJsonResponse(String raw) {
        String prompt = """
                Nội dung sau là phản hồi JSON bị lỗi cú pháp từ một model OCR/thẩm định.
                Hãy sửa thành JSON hợp lệ, chỉ trả về JSON, không markdown, không giải thích.
                Không bịa thêm dữ liệu ngoài nội dung đã có. Nếu thiếu trường thì điền null hoặc mảng rỗng.

                Schema bắt buộc:
                {
                  "docTypeDetected": string|null,
                  "verdict": "CONSISTENT"|"SUSPICIOUS"|"HIGH_RISK"|"UNREADABLE",
                  "trustScore": number,
                  "ownerName": string|null,
                  "organizationName": string|null,
                  "extractedMonthlyIncome": string|null,
                  "findings": string[],
                  "consistencyIssues": string[],
                  "summary": string
                }

                JSON lỗi:
                %s
                """.formatted(raw);
        return gemini.generateContent(List.of(GeminiClient.Part.text(prompt)));
    }

    private String extractJsonObject(String value) {
        String clean = value;
        if (clean.startsWith("```")) {
            clean = clean.replaceAll("^```[a-zA-Z]*\\n?", "").replaceAll("```$", "").trim();
        }
        int start = clean.indexOf('{');
        int end = clean.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return clean.substring(start, end + 1).trim();
        }
        return clean;
    }

    private List<String> parseStringArray(JsonNode arrayNode) {
        List<String> result = new ArrayList<>();
        if (arrayNode.isArray()) {
            for (JsonNode item : arrayNode) {
                String v = item.asText("").trim();
                if (!v.isEmpty() && !"null".equalsIgnoreCase(v)) result.add(v);
            }
        }
        return result;
    }

    private String nullIfBlank(String s) {
        return (s == null || s.isBlank() || "null".equalsIgnoreCase(s)) ? null : s;
    }

    private DocumentCheckResult fallbackUnreadable(String reason) {
        return new DocumentCheckResult(
                null, "UNREADABLE", 0, null, null, null,
                List.of(reason), List.of(),
                "Không thể phân tích chứng từ. " + reason
        );
    }

    private String preview(String value) {
        if (value == null) return "";
        int max = 1000;
        return value.length() <= max ? value : value.substring(0, max) + "...[truncated]";
    }

    private void validateGeminiConfig(AppProperties props) {
        if (!StringUtils.hasText(props.getAi().getGeminiApiKey())) {
            throw new IllegalStateException(
                    "app.ai.enabled=true và app.ai.mode=gemini nhưng GEMINI_API_KEY/app.ai.gemini-api-key đang trống"
            );
        }
        if (!StringUtils.hasText(props.getAi().getGeminiModel())) {
            throw new IllegalStateException(
                    "app.ai.enabled=true và app.ai.mode=gemini nhưng GEMINI_MODEL/app.ai.gemini-model đang trống"
            );
        }
    }
}
