package com.p2plending.loan.security;

import com.p2plending.loan.config.JwtProperties;
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

    public JwtFilter(JwtProperties props) {
        this.publicKey = loadPublicKey(props.getPublicKey());
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest  request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain         chain
    ) throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);
        try {
            Claims claims = parseClaims(token);

            String userId = claims.get("userId", String.class);
            String email  = claims.getSubject();

            @SuppressWarnings("unchecked")
            List<String> roles = claims.get("roles", List.class);

            List<SimpleGrantedAuthority> authorities = (roles == null ? List.<String>of() : roles).stream()
                    .map(SimpleGrantedAuthority::new)
                    .toList();

            AuthenticatedUser principal = new AuthenticatedUser(userId, email, authorities);
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
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private RSAPublicKey loadPublicKey(String raw) {
        if (!StringUtils.hasText(raw)) {
            log.warn("JWT: No RSA public key configured — all tokens will be rejected in production. " +
                     "Set RSA_PUBLIC_KEY env var.");
            // Return a dummy key that will reject every token at verification time
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
            java.security.KeyPairGenerator gen = java.security.KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            return (RSAPublicKey) gen.generateKeyPair().getPublic();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate dummy RSA key", e);
        }
    }
}
