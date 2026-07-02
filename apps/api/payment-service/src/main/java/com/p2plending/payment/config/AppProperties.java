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
    private Tikluy tikluy = new Tikluy();
    private Reconciliation reconciliation = new Reconciliation();
    private Auth auth = new Auth();
    private VnfOtp vnfOtp = new VnfOtp();

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
        private int maxFailAttempts = 5;
    }

    @Data
    public static class VnfOtp {
        private String url = "";
        private int withdrawalFunctionType = 2;
    }

    @Data
    public static class Payment {
        /** Khi true: bỏ qua TIKLUY, dùng mock VA / mock fund transfer cho dev/test */
        private boolean mock = true;
    }

    @Data
    public static class Auth {
        private String serviceUrl = "http://auth-service:8081";
    }

    @Data
    public static class Tikluy {
        private Callback callback = new Callback();
        /** Secret gửi trong header X-VNFITE-Internal-Secret khi gọi TIKLUY balance-adjustment và transfer-money. */
        private String internalSecret = "";

        @Data
        public static class Callback {
            /** Optional until TIKLUY can send X-Internal-Secret; when set it is accepted. */
            private String secret;
            /** Comma-separated exact IP allowlist. Empty keeps current TIKLUY flow compatible. */
            private String allowedIps = "";
        }
    }

    @Data
    public static class Reconciliation {
        private boolean autoDepositFixEnabled = false;
        private TikluyDb tikluyDb = new TikluyDb();

        @Data
        public static class TikluyDb {
            private String host = "";
            private int port = 3306;
            private String database = "VNF_ACCOUNT_MANAGEMENT";
            private String username = "";
            private String password = "";
        }
    }
}
