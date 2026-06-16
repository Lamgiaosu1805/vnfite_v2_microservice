package com.p2plending.payment.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app")
@Data
public class AppProperties {

    private Redis redis = new Redis();
    private Internal internal = new Internal();
    private Otp otp = new Otp();
    private Payment payment = new Payment();

    @Data
    public static class Redis {
        private String namespace = "dev:payment-service";
    }

    @Data
    public static class Internal {
        private String secret;
    }

    @Data
    public static class Otp {
        private boolean mock = true;
    }

    @Data
    public static class Payment {
        /** Khi true: bỏ qua TIKLUY, dùng mock VA / mock fund transfer cho dev/test */
        private boolean mock = true;
    }
}
