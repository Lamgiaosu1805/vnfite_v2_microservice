package com.p2plending.loan.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtFilter jwtFilter;

    /**
     * Cho phép website (domain khác) đọc tin tức/tuyển dụng công khai qua trình duyệt.
     * /api/news chỉ GET; /api/jobs cần thêm POST vì candidate nộp hồ sơ ứng tuyển qua browser cross-origin.
     */
    @Bean
    public CorsConfigurationSource newsCorsConfigurationSource() {
        List<String> allowedOrigins = List.of(
                "https://vnfite.com.vn",
                "https://www.vnfite.com.vn",
                "http://localhost:3000"
        );

        CorsConfiguration newsConfig = new CorsConfiguration();
        newsConfig.setAllowedOrigins(allowedOrigins);
        newsConfig.setAllowedMethods(List.of("GET"));
        newsConfig.setAllowedHeaders(List.of("*"));

        CorsConfiguration jobsConfig = new CorsConfiguration();
        jobsConfig.setAllowedOrigins(allowedOrigins);
        jobsConfig.setAllowedMethods(List.of("GET", "POST"));
        jobsConfig.setAllowedHeaders(List.of("*"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/news/**", newsConfig);
        source.registerCorsConfiguration("/api/jobs/**", jobsConfig);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, CorsConfigurationSource newsCorsConfigurationSource) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(newsCorsConfigurationSource))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Actuator
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/internal/**").permitAll()
                        // Public endpoints
                        .requestMatchers("/api/loans/products").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/news", "/api/news/**").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/jobs", "/api/jobs/**").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/jobs/*/applications").permitAll()
                        // Everything else requires authentication
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
