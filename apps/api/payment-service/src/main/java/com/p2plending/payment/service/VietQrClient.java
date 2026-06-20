package com.p2plending.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2plending.payment.config.AppProperties;
import com.p2plending.payment.dto.response.BankCatalogItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Danh sách ngân hàng:
 * - Primary source: banks.json (danh sách TIKLUY đầy đủ, gồm LIOBANK/CAKE/Ubank)
 * - Logo enrich: VietQR api.vietqr.io/v2/banks (ghép theo bankCode)
 * - Kết quả cache Redis 24h
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VietQrClient {

    private static final String VIETQR_URL = "https://api.vietqr.io/v2/banks";
    private static final String CACHE_KEY_SUFFIX = ":bank-catalog";
    private static final Duration CACHE_TTL = Duration.ofHours(24);

    private final RestTemplate restTemplate;
    private final StringRedisTemplate redisTemplate;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    public List<BankCatalogItem> getBankList() {
        String cacheKey = appProperties.getRedis().getNamespace() + CACHE_KEY_SUFFIX;

        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached,
                        objectMapper.getTypeFactory().constructCollectionType(List.class, BankCatalogItem.class));
            } catch (Exception e) {
                log.warn("Failed to deserialize cached bank list, re-fetching", e);
            }
        }

        List<BankCatalogItem> banks = buildMergedList();
        if (!banks.isEmpty()) {
            try {
                redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(banks), CACHE_TTL);
            } catch (Exception e) {
                log.warn("Failed to cache bank list to Redis", e);
            }
        }
        return banks;
    }

    private List<BankCatalogItem> buildMergedList() {
        Map<String, String> logoMap = fetchLogoMapFromVietQr();
        List<BankCatalogItem> staticBanks = loadStaticBankList();

        List<BankCatalogItem> result = new ArrayList<>();
        for (BankCatalogItem bank : staticBanks) {
            result.add(BankCatalogItem.builder()
                    .bankCode(bank.getBankCode())
                    .bankName(bank.getBankName())
                    .bankShortName(bank.getBankShortName())
                    .icon(logoMap.get(bank.getBankCode()))
                    .build());
        }
        log.info("Built bank catalog: {} banks (static list + VietQR logos)", result.size());
        return result;
    }

    private List<BankCatalogItem> loadStaticBankList() {
        try {
            ClassPathResource resource = new ClassPathResource("banks.json");
            JsonNode array = objectMapper.readTree(resource.getInputStream());
            List<BankCatalogItem> result = new ArrayList<>();
            for (JsonNode node : array) {
                result.add(BankCatalogItem.builder()
                        .bankCode(node.path("bankCode").asText())
                        .bankName(node.path("bankName").asText())
                        .bankShortName(node.path("bankShortName").asText())
                        .build());
            }
            log.info("Loaded {} banks from static banks.json", result.size());
            return result;
        } catch (Exception e) {
            log.error("Failed to load static bank list: {}", e.getMessage());
            return List.of();
        }
    }

    private Map<String, String> fetchLogoMapFromVietQr() {
        Map<String, String> logoMap = new HashMap<>();
        try {
            ResponseEntity<JsonNode> resp = restTemplate.getForEntity(VIETQR_URL, JsonNode.class);
            JsonNode body = resp.getBody();
            if (body != null && "00".equals(body.path("code").asText())) {
                for (JsonNode bank : body.path("data")) {
                    String code = bank.path("code").asText();
                    String logo = bank.path("logo").asText();
                    if (!code.isBlank() && !logo.isBlank()) {
                        logoMap.put(code, logo);
                    }
                }
                log.info("VietQR logo map: {} entries", logoMap.size());
            }
        } catch (Exception e) {
            log.warn("Failed to fetch VietQR logo map: {}", e.getMessage());
        }
        return logoMap;
    }

    public void evictCache() {
        String cacheKey = appProperties.getRedis().getNamespace() + CACHE_KEY_SUFFIX;
        redisTemplate.delete(cacheKey);
    }
}
