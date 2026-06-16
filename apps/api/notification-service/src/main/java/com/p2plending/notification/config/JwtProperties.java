package com.p2plending.notification.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {
    private String publicKey;
    private String issuer = "vnfite-auth";
    private String audience = "vnfite-api";
}
