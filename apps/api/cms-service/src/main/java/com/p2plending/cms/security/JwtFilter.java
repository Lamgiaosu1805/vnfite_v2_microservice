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
                List<String> safeRoles = roles == null ? List.<String>of() : roles;
                @SuppressWarnings("unchecked")
                List<String> permissions = claims.get("permissions", List.class);
                List<String> safePermissions = permissions == null ? List.<String>of() : permissions;
                // Quyền lẻ dùng authority thô (không tiền tố ROLE_) cho hasAuthority(...)
                java.util.Set<String> permissionSet = safePermissions.stream()
                        .filter(p -> p != null && !p.isBlank())
                        .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
                List<SimpleGrantedAuthority> authorities = java.util.stream.Stream.concat(
                                safeRoles.stream(), permissionSet.stream())
                        .map(SimpleGrantedAuthority::new).toList();
                // Tập vai trò đã bỏ tiền tố ROLE_ để phân quyền trong CmsPrincipal
                java.util.Set<String> roleSet = safeRoles.stream()
                        .map(r -> r.replaceFirst("^ROLE_", ""))
                        .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
                // Vai trò chính = phần tử đầu (giữ để tương thích)
                String roleValue = roleSet.isEmpty() ? null : roleSet.iterator().next();
                var auth = new UsernamePasswordAuthenticationToken(
                        new CmsPrincipal(adminUserId, claims.getSubject(),
                                claims.get("fullName", String.class),
                                claims.get("email", String.class), roleValue, roleSet, permissionSet), null, authorities);
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
