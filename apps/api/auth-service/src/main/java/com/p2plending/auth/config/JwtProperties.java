package com.p2plending.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "jwt")
@Data
public class JwtProperties {

    /** Base64-encoded PKCS8 RSA private key. PEM headers are optional. */
    private String privateKey = "";

    /** Base64-encoded X509 RSA public key. PEM headers are optional. */
    private String publicKey = "";

    /** Access token lifetime in seconds (default 15 minutes). */
    private long accessTokenExpiry = 900;

    /** Refresh token lifetime in seconds (default 24 hours). */
    private long refreshTokenExpiry = 86400;
}
