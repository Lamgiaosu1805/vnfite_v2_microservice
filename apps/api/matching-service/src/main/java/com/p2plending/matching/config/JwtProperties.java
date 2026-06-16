package com.p2plending.matching.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "jwt")
@Data
public class JwtProperties {
    private String publicKey = "";
    private String issuer = "vnfite-auth";
    private String audience = "vnfite-api";
}
