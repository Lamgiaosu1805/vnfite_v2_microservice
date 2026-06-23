package com.p2plending.payment.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "tikluy")
@Data
public class TikluyProperties {
    private String baseUrl;
    private String clientId;
    private String clientSecret;
    private String source = "VNFITE";
    /** Secret gửi trong X-VNFITE-Internal-Secret header khi gọi TIKLUY balance-adjustment. */
    private String internalSecret = "";
}
