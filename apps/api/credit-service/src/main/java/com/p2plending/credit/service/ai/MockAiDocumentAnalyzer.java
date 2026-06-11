package com.p2plending.credit.service.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Mock khi app.ai.enabled=false — phân tích chứng từ bắt buộc cần AI,
 * trả null để service báo lỗi rõ ràng cho caller.
 */
@Service
@ConditionalOnProperty(name = "app.ai.enabled", havingValue = "false", matchIfMissing = true)
@Slf4j
public class MockAiDocumentAnalyzer implements AiDocumentAnalyzer {

    @Override
    public DocumentCheckResult analyze(String mimeType, String fileBase64, String context) {
        log.debug("AI document analysis đang tắt (app.ai.enabled=false)");
        return null;
    }
}
