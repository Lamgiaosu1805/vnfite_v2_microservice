package com.p2plending.credit.service.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.*;
import com.p2plending.credit.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Claude vision đọc chứng từ thu nhập: trích xuất dữ liệu, kiểm tra
 * tính nhất quán nội tại và đối chiếu với thông tin khai báo.
 */
@Service
@ConditionalOnProperty(name = "app.ai.enabled", havingValue = "true")
@Slf4j
public class ClaudeAiDocumentAnalyzer implements AiDocumentAnalyzer {

    private static final String SYSTEM_PROMPT = """
            Bạn là chuyên gia thẩm định chứng từ tài chính của nền tảng cho vay ngang hàng VNFITE \
            tại Việt Nam. Đây là quy trình phòng chống gian lận hợp pháp của tổ chức tài chính: \
            người gọi vốn tự nguyện nộp chứng từ để chứng minh thu nhập khi vay vốn.

            Nhiệm vụ khi nhận một chứng từ (sao kê lương, sao kê ngân hàng, HĐLĐ, ĐKKD...):

            1. TRÍCH XUẤT: loại chứng từ, tên chủ tài khoản/người lao động, tổ chức phát hành, \
            thu nhập/lương hàng tháng (nếu nhiều tháng thì lấy trung bình).

            2. KIỂM TRA NHẤT QUÁN NỘI TẠI:
            - Sao kê ngân hàng: cộng trừ SỐ DƯ CHẠY từng dòng có khớp không (dấu hiệu sửa số phổ biến \
            nhất là sửa một dòng mà quên sửa số dư các dòng sau)
            - Định dạng ngày tháng có nhất quán và hợp lý không (giao dịch tương lai, thứ tự lộn xộn)
            - Font chữ, căn lề, khoảng cách có chỗ nào khác biệt bất thường không
            - Logo, mẫu biểu có đúng định dạng của ngân hàng/tổ chức đó không
            - Con dấu, chữ ký (với HĐLĐ, xác nhận lương)

            3. ĐỐI CHIẾU với thông tin người vay khai báo (tên, thu nhập, nơi làm việc) — \
            liệt kê mọi điểm không khớp vào consistencyIssues.

            4. KẾT LUẬN verdict + trustScore. LƯU Ý: bạn KHÔNG THỂ khẳng định 100% chứng từ giả — \
            chỉ cảnh báo mức độ tin cậy dựa trên bằng chứng quan sát được. Mọi findings phải nêu \
            cụ thể vị trí/lý do, không phán đoán mơ hồ. Quyết định cuối cùng thuộc về admin thẩm định.""";

    private final AnthropicClient client;
    private final String model;

    public ClaudeAiDocumentAnalyzer(AppProperties props) {
        this.client = AnthropicOkHttpClient.builder()
                .apiKey(props.getAi().getApiKey())
                .build();
        this.model = props.getAi().getModel();
    }

    @Override
    public DocumentCheckResult analyze(String mimeType, String fileBase64, String context) {
        try {
            List<ContentBlockParam> blocks = new ArrayList<>();

            if ("application/pdf".equalsIgnoreCase(mimeType)) {
                blocks.add(ContentBlockParam.ofDocument(DocumentBlockParam.builder()
                        .base64Source(fileBase64)
                        .build()));
            } else {
                blocks.add(ContentBlockParam.ofImage(ImageBlockParam.builder()
                        .source(Base64ImageSource.builder()
                                .mediaType(Base64ImageSource.MediaType.of(mimeType.toLowerCase()))
                                .data(fileBase64)
                                .build())
                        .build()));
            }
            blocks.add(ContentBlockParam.ofText(TextBlockParam.builder().text(context).build()));

            StructuredMessageCreateParams<DocumentCheckResult> params = MessageCreateParams.builder()
                    .model(model)
                    .maxTokens(16000L)
                    .outputConfig(DocumentCheckResult.class)
                    .thinking(ThinkingConfigAdaptive.builder().build())
                    .system(SYSTEM_PROMPT)
                    .addUserMessageOfBlockParams(blocks)
                    .build();

            return client.messages().create(params).content().stream()
                    .flatMap(block -> block.text().stream())
                    .findFirst()
                    .map(textBlock -> textBlock.text())
                    .orElse(null);

        } catch (Exception e) {
            log.error("Claude document analysis failed: {}", e.getMessage());
            throw new RuntimeException("Phân tích chứng từ thất bại: " + e.getMessage(), e);
        }
    }
}
