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
        private String secret = "dev-internal-secret";
    }

    @Data
    public static class Scoring {
        private String modelVersion = "scorecard-v1.0";
        private int scoreTtlDays = 90;
    }

    @Data
    public static class Ai {
        /** Khi false: dùng MockAiRiskAssessor, không gọi Claude API */
        private boolean enabled = false;
        private String apiKey = "";
        private String model = "claude-opus-4-8";
    }
}
