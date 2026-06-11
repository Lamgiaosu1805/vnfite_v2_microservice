package com.p2plending.credit.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2plending.credit.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Dùng Gemini Flash để sinh advisory cho admin thẩm định.
 * Bật bằng: APP_AI_ENABLED=true + APP_AI_MODE=gemini
 *
 * Kết quả chỉ mang tính TƯ VẤN — không tự quyết định duyệt/từ chối.
 */
@Service
@ConditionalOnExpression("'${app.ai.enabled:false}'.equals('true') and '${app.ai.mode:claude}'.equals('gemini')")
@Slf4j
public class GeminiAiRiskAssessor implements AiRiskAssessor {

    private static final String SCHEMA = """
            Trả về JSON với cấu trúc:
            {
              "summary": "2-4 câu tóm tắt mức độ rủi ro tổng thể bằng tiếng Việt",
              "riskFlags": ["điểm bất thường 1", "điểm bất thường 2"],
              "recommendation": "1-2 câu gợi ý cho admin thẩm định bằng tiếng Việt"
            }
            riskFlags là mảng rỗng nếu không có điểm nào đáng ngờ.
            Tuyệt đối không tự quyết định duyệt hay từ chối — chỉ phân tích và tư vấn.
            """;

    private final GeminiClient gemini;
    private final ObjectMapper objectMapper;

    public GeminiAiRiskAssessor(AppProperties props, RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.gemini       = new GeminiClient(restTemplate, objectMapper,
                props.getAi().getGeminiApiKey(), props.getAi().getGeminiModel());
        this.objectMapper = objectMapper;
        log.info("GeminiAiRiskAssessor bật — model={}", props.getAi().getGeminiModel());
    }

    @Override
    public AiRiskAssessment assess(String context) {
        String prompt = """
                Bạn là chuyên gia thẩm định tín dụng của nền tảng cho vay ngang hàng VNFITE tại Việt Nam.
                Phân tích hồ sơ sau và đưa ra đánh giá rủi ro ngắn gọn, khách quan để hỗ trợ admin thẩm định.

                %s

                %s
                """.formatted(context, SCHEMA);

        try {
            String json = gemini.generateContent(List.of(GeminiClient.Part.text(prompt)));
            if (json == null || json.isBlank()) return null;

            JsonNode node = objectMapper.readTree(json);
            String summary        = node.path("summary").asText(null);
            String recommendation = node.path("recommendation").asText(null);
            List<String> flags    = new ArrayList<>();
            for (JsonNode flag : node.path("riskFlags")) {
                String v = flag.asText("").trim();
                if (!v.isEmpty()) flags.add(v);
            }
            if (summary == null) return null;
            return new AiRiskAssessment(summary, flags, recommendation);

        } catch (Exception e) {
            log.error("Gemini risk assessment failed (bỏ qua, không chặn chấm điểm): {}", e.getMessage());
            return null;
        }
    }
}
