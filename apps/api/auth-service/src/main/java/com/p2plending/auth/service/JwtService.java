package com.p2plending.auth.service;

import com.p2plending.auth.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.List;

@Service
@Slf4j
public class JwtService {

    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_TYPE  = "type";
    private static final String TYPE_REFRESH = "refresh";

    private final RSAPrivateKey privateKey;
    private final RSAPublicKey  publicKey;
    private final long accessTokenExpiry;
    private final long refreshTokenExpiry;

    public JwtService(JwtProperties props) throws NoSuchAlgorithmException {
        this.accessTokenExpiry  = props.getAccessTokenExpiry();
        this.refreshTokenExpiry = props.getRefreshTokenExpiry();

        KeyPair pair = resolveKeyPair(props);
        this.privateKey = (RSAPrivateKey) pair.getPrivate();
        this.publicKey  = (RSAPublicKey)  pair.getPublic();
    }

    // ── Token generation ──────────────────────────────────────────

    public String generateAccessToken(UserDetails userDetails, String userId) {
        List<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        return Jwts.builder()
                .subject(userDetails.getUsername())
                .claim("userId", userId)
                .claim(CLAIM_ROLES, roles)
                .issuedAt(new Date())
                .expiration(expiresAt(accessTokenExpiry))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    public String generateRefreshToken(String subject) {
        return Jwts.builder()
                .subject(subject)
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .issuedAt(new Date())
                .expiration(expiresAt(refreshTokenExpiry))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    // ── Token validation ──────────────────────────────────────────

    public String extractSubject(String token) {
        return parseClaims(token).getSubject();
    }

    public boolean isAccessTokenValid(String token, UserDetails userDetails) {
        try {
            Claims claims = parseClaims(token);
            return userDetails.getUsername().equals(claims.getSubject())
                    && !isExpired(claims)
                    && !TYPE_REFRESH.equals(claims.get(CLAIM_TYPE));
        } catch (JwtException e) {
            return false;
        }
    }

    public boolean isRefreshTokenValid(String token) {
        try {
            Claims claims = parseClaims(token);
            return TYPE_REFRESH.equals(claims.get(CLAIM_TYPE)) && !isExpired(claims);
        } catch (JwtException e) {
            return false;
        }
    }

    public long getAccessTokenExpiry() {
        return accessTokenExpiry;
    }

    // ── Internals ─────────────────────────────────────────────────

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private boolean isExpired(Claims claims) {
        return claims.getExpiration().before(new Date());
    }

    private Date expiresAt(long seconds) {
        return new Date(System.currentTimeMillis() + seconds * 1_000L);
    }

    private KeyPair resolveKeyPair(JwtProperties props) throws NoSuchAlgorithmException {
        if (StringUtils.hasText(props.getPrivateKey()) && StringUtils.hasText(props.getPublicKey())) {
            try {
                RSAPrivateKey priv = parsePrivateKey(props.getPrivateKey());
                RSAPublicKey  pub  = parsePublicKey(props.getPublicKey());
                log.info("JWT: RSA key pair loaded from configuration");
                return new KeyPair(pub, priv);
            } catch (Exception e) {
                log.warn("JWT: Failed to load configured RSA keys — falling back to ephemeral pair: {}", e.getMessage());
            }
        }
        log.warn("JWT: No RSA keys configured — generating ephemeral pair (NOT suitable for production)");
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        return gen.generateKeyPair();
    }

    private RSAPrivateKey parsePrivateKey(String raw) throws Exception {
        byte[] bytes = Base64.getDecoder().decode(stripPemHeaders(raw));
        return (RSAPrivateKey) KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(bytes));
    }

    private RSAPublicKey parsePublicKey(String raw) throws Exception {
        byte[] bytes = Base64.getDecoder().decode(stripPemHeaders(raw));
        return (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(bytes));
    }

    private String stripPemHeaders(String key) {
        return key.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
    }
}
