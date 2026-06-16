package com.p2plending.auth.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

@Component
@Order(2)
@Slf4j
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final int MAX_BODY_LENGTH = 2000;

    private static final Pattern SENSITIVE = Pattern.compile(
            "\"(password|newPassword|currentPassword|oldPassword|accessToken|refreshToken|resetToken|otp|token|cccdNumber|frontImage|backImage|portraitImage)\"\\s*:\\s*\"[^\"]*\"",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern SKIP_BODY_URI = Pattern.compile(
            ".*/(kyc|documents/upload).*",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain
    ) throws ServletException, IOException {

        ContentCachingRequestWrapper  req  = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper resp = new ContentCachingResponseWrapper(response);

        long   start  = System.currentTimeMillis();
        String method = request.getMethod();
        String uri    = request.getRequestURI();
        String ip     = resolveClientIp(request);

        try {
            chain.doFilter(req, resp);
        } finally {
            long duration = System.currentTimeMillis() - start;
            int  status   = resp.getStatus();

            String reqBody  = shouldSkipBody(uri) ? "[body omitted]" : readBody(req.getContentAsByteArray(),  req.getCharacterEncoding());
            String respBody = shouldSkipBody(uri) ? "[body omitted]" : readBody(resp.getContentAsByteArray(), resp.getCharacterEncoding());

            String reqLog  = maskSensitive(reqBody);
            String respLog = maskSensitive(respBody);

            log.info("--> {} {} [ip={}]\n    REQ : {}", method, uri, ip, reqLog);

            if (status >= 500) {
                log.error("<-- {} {} {} ({}ms)\n    RESP: {}", method, uri, status, duration, respLog);
            } else if (status >= 400) {
                log.warn("<-- {} {} {} ({}ms)\n    RESP: {}",  method, uri, status, duration, respLog);
            } else {
                log.info("<-- {} {} {} ({}ms)\n    RESP: {}",  method, uri, status, duration, respLog);
            }

            // bắt buộc: copy body trở lại response để client nhận được
            resp.copyBodyToResponse();
        }
    }

    private String readBody(byte[] bytes, String encoding) {
        if (bytes == null || bytes.length == 0) return "-";
        try {
            Charset charset = StringUtils.hasText(encoding)
                    ? Charset.forName(encoding)
                    : StandardCharsets.UTF_8;
            String body = new String(bytes, charset);
            if (body.length() > MAX_BODY_LENGTH) {
                return body.substring(0, MAX_BODY_LENGTH) + "... [truncated]";
            }
            return body;
        } catch (Exception e) {
            return "[unreadable]";
        }
    }

    private String maskSensitive(String body) {
        if ("-".equals(body)) return body;
        return SENSITIVE.matcher(body).replaceAll("\"$1\":\"***\"");
    }

    private boolean shouldSkipBody(String uri) {
        return uri != null && SKIP_BODY_URI.matcher(uri).matches();
    }

    private String resolveClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(ip)) return ip.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}
