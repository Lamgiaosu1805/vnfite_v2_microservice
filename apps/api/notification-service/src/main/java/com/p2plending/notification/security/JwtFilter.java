package com.p2plending.notification.security;

import com.p2plending.notification.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;

@Component
@Slf4j
public class JwtFilter extends OncePerRequestFilter {

    private final RSAPublicKey publicKey;
    private final JwtProperties props;

    public JwtFilter(JwtProperties props) {
        this.props = props;
        this.publicKey = loadPublicKey(props.getPublicKey());
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain
    ) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        try {
            Claims claims = parseClaims(header.substring(7));
            if (!"access".equals(claims.get("type", String.class))) {
                throw new JwtException("JWT token type is not access");
            }
            String userId = claims.get("userId", String.class);
            if (!StringUtils.hasText(userId)) {
                throw new JwtException("JWT missing userId");
            }
            String subject = claims.getSubject();

            @SuppressWarnings("unchecked")
            List<String> roles = claims.get("roles", List.class);
            List<SimpleGrantedAuthority> authorities = (roles == null ? List.<String>of() : roles).stream()
                    .map(SimpleGrantedAuthority::new)
                    .toList();

            AuthenticatedUser principal = new AuthenticatedUser(userId, subject, authorities);
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(principal, null, authorities);
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (ExpiredJwtException e) {
            log.debug("Expired JWT on {}", request.getRequestURI());
        } catch (JwtException e) {
            log.debug("Invalid JWT on {}: {}", request.getRequestURI(), e.getMessage());
        }

        chain.doFilter(request, response);
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(publicKey)
                .requireIssuer(props.getIssuer())
                .requireAudience(props.getAudience())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private RSAPublicKey loadPublicKey(String raw) {
        if (!StringUtils.hasText(raw)) {
            log.warn("JWT: RSA public key is not configured for notification-service");
            return generateDummyPublicKey();
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(
                    raw.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", ""));
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(bytes));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load RSA public key for JWT validation", e);
        }
    }

    private RSAPublicKey generateDummyPublicKey() {
        try {
            var gen = java.security.KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            return (RSAPublicKey) gen.generateKeyPair().getPublic();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate dummy RSA key", e);
        }
    }
}
