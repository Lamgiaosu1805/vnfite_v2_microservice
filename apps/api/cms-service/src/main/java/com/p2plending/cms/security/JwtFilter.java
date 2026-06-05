package com.p2plending.cms.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import com.p2plending.cms.service.CmsJwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final CmsJwtService jwtService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest req,
                                    @NonNull HttpServletResponse res,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Claims claims = jwtService.parse(header.substring(7));
                // pendingToken chỉ dùng cho TOTP endpoints, không cấp quyền truy cập
                if (jwtService.isPendingToken(claims)) {
                    chain.doFilter(req, res);
                    return;
                }
                String adminUserId = claims.get("adminUserId", String.class);
                @SuppressWarnings("unchecked")
                List<String> roles = claims.get("roles", List.class);
                List<SimpleGrantedAuthority> authorities =
                        (roles == null ? List.<String>of() : roles)
                                .stream().map(SimpleGrantedAuthority::new).toList();
                // Lấy role đầu tiên, bỏ tiền tố ROLE_ để lưu vào CmsPrincipal
                String roleValue = (roles != null && !roles.isEmpty())
                        ? roles.get(0).replaceFirst("^ROLE_", "")
                        : null;
                var auth = new UsernamePasswordAuthenticationToken(
                        new CmsPrincipal(adminUserId, claims.getSubject(),
                                claims.get("email", String.class), roleValue), null, authorities);
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (ExpiredJwtException e) {
                log.debug("Expired CMS JWT");
            } catch (JwtException e) {
                log.debug("Invalid CMS JWT: {}", e.getMessage());
            }
        }
        chain.doFilter(req, res);
    }
}
