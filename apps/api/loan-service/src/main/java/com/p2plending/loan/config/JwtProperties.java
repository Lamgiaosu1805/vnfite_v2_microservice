package com.p2plending.loan.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "jwt")
@Data
public class JwtProperties {

    /** Base64-encoded X509 RSA public key used to verify tokens issued by auth-service. */
    private String publicKey = "";
    private String issuer = "vnfite-auth";
    private String audience = "vnfite-api";
}
