package com.p2plending.credit.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app")
@Data
public class AppProperties {

    private Internal internal = new Internal();
    private Scoring scoring = new Scoring();
    private Ai ai = new Ai();

    @Data
    public static class Internal {
        private String secret;
    }

    @Data
    public static class Scoring {
        private String modelVersion = "scorecard-v1.0";
        private int scoreTtlDays = 90;
    }

    @Data
    public static class Ai {
        /**
         * false       → mock (null, không có advisory)
         * true + demo → DemoAiRiskAssessor (fake text, không cần key)
         * true + gemini → Gemini Flash (miễn phí, cần GEMINI_API_KEY)
         * true + claude → Claude API (cần ANTHROPIC_API_KEY)
         */
        private boolean enabled = false;
        private String mode = "demo";
        // Claude
        private String apiKey = "";
        private String model = "claude-opus-4-8";
        // Gemini
        private String geminiApiKey = "";
        private String geminiModel = "gemini-2.5-flash";
    }
}
