package com.p2plending.fec.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.fec")
public class FecProperties {
    private boolean enabled = true;
    private String receiveLeadUrl;
    private String partnerCode;
    private String leadSource;
    private String bearerToken;
    private String partnerPrivateKey;
    private String partnerPublicKey;
    private String fecPublicKey;
    private String callbackApiKey;
    private int connectTimeoutMs = 10000;
    private int readTimeoutMs = 30000;
}
