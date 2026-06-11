package com.p2plending.credit.service.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Mock khi app.ai.enabled=false — không gọi Claude API, trả null
 * để CreditScoringService bỏ qua phần AI.
 */
@Service
@ConditionalOnProperty(name = "app.ai.enabled", havingValue = "false", matchIfMissing = true)
@Slf4j
public class MockAiRiskAssessor implements AiRiskAssessor {

    @Override
    public AiRiskAssessment assess(String context) {
        log.debug("AI risk assessment đang tắt (app.ai.enabled=false) — bỏ qua");
        return null;
    }
}
