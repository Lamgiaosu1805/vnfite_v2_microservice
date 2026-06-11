package com.p2plending.credit.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestClientConfig {

    @Bean
    @Qualifier("restTemplate")
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        // Tải file chứng từ có thể lớn (PDF sao kê) → timeout rộng tay
        return builder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(30))
                .build();
    }

    @Bean
    @Qualifier("geminiRestTemplate")
    public RestTemplate geminiRestTemplate(RestTemplateBuilder builder) {
        // Gemini phân tích PDF/base64 có thể lâu hơn fetch file-manager.
        return builder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(90))
                .build();
    }
}
