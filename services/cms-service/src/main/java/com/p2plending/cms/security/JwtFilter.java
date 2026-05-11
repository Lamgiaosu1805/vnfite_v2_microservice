package com.p2plending.cms.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    public JwtFilter(@Value("${jwt.public-key:}") String rawKey) {
        this.publicKey = loadKey(rawKey);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest req,
                                    @NonNull HttpServletResponse res,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Claims claims = Jwts.parser().verifyWith(publicKey).build()
                        .parseSignedClaims(header.substring(7)).getPayload();
                String userId = claims.get("userId", String.class);
                @SuppressWarnings("unchecked")
                List<String> roles = claims.get("roles", List.class);
                List<SimpleGrantedAuthority> authorities =
                        (roles == null ? List.<String>of() : roles)
                                .stream().map(SimpleGrantedAuthority::new).toList();
                var auth = new UsernamePasswordAuthenticationToken(
                        new CmsPrincipal(userId, claims.getSubject()), null, authorities);
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (ExpiredJwtException e) {
                log.debug("Expired JWT");
            } catch (JwtException e) {
                log.debug("Invalid JWT: {}", e.getMessage());
            }
        }
        chain.doFilter(req, res);
    }

    private RSAPublicKey loadKey(String raw) {
        if (!StringUtils.hasText(raw)) {
            log.warn("CMS: No RSA public key configured — generating ephemeral (dev only)");
            try {
                var gen = java.security.KeyPairGenerator.getInstance("RSA");
                gen.initialize(2048);
                return (RSAPublicKey) gen.generateKeyPair().getPublic();
            } catch (Exception e) { throw new IllegalStateException(e); }
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(
                    raw.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", ""));
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(bytes));
        } catch (Exception e) { throw new IllegalStateException("Failed to load RSA public key", e); }
    }
}
