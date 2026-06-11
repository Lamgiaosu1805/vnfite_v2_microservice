package com.p2plending.credit.service.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.p2plending.credit.config.AppProperties;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.ThinkingConfigAdaptive;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Gọi Claude API để sinh đánh giá rủi ro cho admin thẩm định.
 * Bật bằng APP_AI_ENABLED=true + ANTHROPIC_API_KEY.
 *
 * Mọi lỗi (timeout, hết quota, key sai) đều được nuốt và trả null —
 * AI là lớp tư vấn bổ sung, không bao giờ chặn luồng chấm điểm.
 */
@Service
@ConditionalOnProperty(name = "app.ai.enabled", havingValue = "true")
@Slf4j
public class ClaudeAiRiskAssessor implements AiRiskAssessor {

    private static final String SYSTEM_PROMPT = """
            Bạn là chuyên gia thẩm định tín dụng của nền tảng cho vay ngang hàng VNFITE tại Việt Nam.
            Nhiệm vụ: phân tích hồ sơ người gọi vốn và đưa ra đánh giá rủi ro NGẮN GỌN, KHÁCH QUAN \
            bằng tiếng Việt để hỗ trợ admin thẩm định.

            Yêu cầu:
            - summary: 2-4 câu tóm tắt mức độ rủi ro tổng thể của hồ sơ
            - riskFlags: liệt kê các điểm bất thường hoặc đáng lưu ý (mâu thuẫn giữa nghề nghiệp và \
            thu nhập khai báo, mục đích vay mơ hồ, tỷ lệ nợ cao, hồ sơ thiếu dữ liệu...). \
            Không có gì đáng ngại thì trả mảng rỗng.
            - recommendation: 1-2 câu gợi ý cho admin (vd "nên yêu cầu bổ sung sao kê lương")

            Tuyệt đối không tự quyết định duyệt hay từ chối — chỉ phân tích và tư vấn. \
            Quyết định cuối cùng thuộc về admin thẩm định.""";

    private final AnthropicClient client;
    private final String model;

    public ClaudeAiRiskAssessor(AppProperties props) {
        this.client = AnthropicOkHttpClient.builder()
                .apiKey(props.getAi().getApiKey())
                .build();
        this.model = props.getAi().getModel();
        log.info("ClaudeAiRiskAssessor bật — model={}", model);
    }

    @Override
    public AiRiskAssessment assess(String context) {
        try {
            StructuredMessageCreateParams<AiRiskAssessment> params = MessageCreateParams.builder()
                    .model(model)
                    .maxTokens(16000L)
                    .outputConfig(AiRiskAssessment.class)
                    .thinking(ThinkingConfigAdaptive.builder().build())
                    .system(SYSTEM_PROMPT)
                    .addUserMessage(context)
                    .build();

            return client.messages().create(params).content().stream()
                    .flatMap(block -> block.text().stream())
                    .findFirst()
                    .map(textBlock -> textBlock.text())
                    .orElse(null);

        } catch (Exception e) {
            log.error("Claude risk assessment failed (bỏ qua, không chặn chấm điểm): {}", e.getMessage());
            return null;
        }
    }
}
