package com.p2plending.cms.service;

import com.p2plending.cms.domain.entity.CmsAdminUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Set;

@Service
public class CmsJwtService {

    private final SecretKey key;
    private final long accessTokenExpiry;

    public CmsJwtService(
            @Value("${cms.jwt.secret:}") String rawSecret,
            @Value("${cms.jwt.access-token-expiry:28800}") long accessTokenExpiry
    ) {
        String secret = StringUtils.hasText(rawSecret)
                ? rawSecret
                : "dev-only-cms-jwt-secret-change-me-at-least-32-bytes";
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiry = accessTokenExpiry;
    }

    /** Token ngắn hạn (5 phút) dùng để xác thực TOTP sau bước nhập mật khẩu */
    public String generatePendingToken(String username, String adminUserId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim("adminUserId", adminUserId)
                .claim("pending", true)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(300)))
                .signWith(key)
                .compact();
    }

    public boolean isPendingToken(Claims claims) {
        return Boolean.TRUE.equals(claims.get("pending", Boolean.class));
    }

    public String generateAccessToken(CmsAdminUser admin) {
        Instant now = Instant.now();
        // Hợp nhất vai trò chính (nhãn cũ) + tập vai trò phòng ban → mọi @PreAuthorize
        // cũ lẫn mới đều khớp trong giai đoạn chuyển tiếp.
        Set<String> authorities = new LinkedHashSet<>();
        if (admin.getRole() != null && !admin.getRole().isBlank()) {
            authorities.add("ROLE_" + admin.getRole());
        }
        if (admin.getRoles() != null) {
            admin.getRoles().stream()
                    .filter(r -> r != null && !r.isBlank())
                    .forEach(r -> authorities.add("ROLE_" + r));
        }
        return Jwts.builder()
                .subject(admin.getUsername())
                .claim("adminUserId", admin.getId())
                .claim("fullName", admin.getFullName())
                .claim("email", admin.getEmail())
                .claim("roles", new ArrayList<>(authorities))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessTokenExpiry)))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long getAccessTokenExpiry() {
        return accessTokenExpiry;
    }
}
