package com.p2plending.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2plending.payment.config.AppProperties;
import com.p2plending.payment.dto.response.BankCatalogItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * Aggregator danh sách ngân hàng:
 * - Primary: TIKLUY /common/bank (93+ ngân hàng, có LIOBANK/CAKE/Ubank riêng)
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
    private final TikluyClient tikluyClient;

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

        List<JsonNode> tikluyBanks = tikluyClient.getBankList();
        if (!tikluyBanks.isEmpty()) {
            return mapTikluyBanks(tikluyBanks, logoMap);
        }

        // fallback: chỉ dùng VietQR nếu TIKLUY không trả về
        log.warn("TIKLUY bank list empty, falling back to VietQR only");
        return fetchFromVietQr(logoMap);
    }

    private List<BankCatalogItem> mapTikluyBanks(List<JsonNode> nodes, Map<String, String> logoMap) {
        List<BankCatalogItem> result = new ArrayList<>();
        for (JsonNode bank : nodes) {
            // TIKLUY có thể trả camelCase hoặc snake_case
            String code      = textOf(bank, "bankCode", "bank_code");
            String name      = textOf(bank, "bankName", "bank_name");
            String shortName = textOf(bank, "bankShortName", "bank_short_name");
            if (code.isBlank()) continue;
            result.add(BankCatalogItem.builder()
                    .bankCode(code)
                    .bankName(name)
                    .bankShortName(shortName.isBlank() ? code : shortName)
                    .icon(logoMap.get(code))
                    .build());
        }
        log.info("Built bank catalog: {} banks (TIKLUY primary + VietQR logos)", result.size());
        return result;
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

    private List<BankCatalogItem> fetchFromVietQr(Map<String, String> logoMap) {
        try {
            ResponseEntity<JsonNode> resp = restTemplate.getForEntity(VIETQR_URL, JsonNode.class);
            JsonNode body = resp.getBody();
            if (body == null || !"00".equals(body.path("code").asText())) {
                return List.of();
            }
            List<BankCatalogItem> result = new ArrayList<>();
            for (JsonNode bank : body.path("data")) {
                if (bank.path("lookupSupported").asInt(0) == 0) continue;
                String code = bank.path("code").asText();
                result.add(BankCatalogItem.builder()
                        .bankCode(code)
                        .bankName(bank.path("name").asText())
                        .bankShortName(bank.path("shortName").asText())
                        .icon(logoMap.getOrDefault(code, bank.path("logo").asText()))
                        .build());
            }
            return result;
        } catch (Exception e) {
            log.error("Failed to fetch from VietQR: {}", e.getMessage());
            return List.of();
        }
    }

    private String textOf(JsonNode node, String... fields) {
        for (String f : fields) {
            JsonNode n = node.path(f);
            if (!n.isMissingNode() && !n.isNull()) {
                String v = n.asText().trim();
                if (!v.isBlank()) return v;
            }
        }
        return "";
    }

    public void evictCache() {
        String cacheKey = appProperties.getRedis().getNamespace() + CACHE_KEY_SUFFIX;
        redisTemplate.delete(cacheKey);
    }
}
