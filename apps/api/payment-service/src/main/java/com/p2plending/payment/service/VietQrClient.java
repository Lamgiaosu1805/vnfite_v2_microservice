package com.p2plending.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.p2plending.payment.config.AppProperties;
import com.p2plending.payment.dto.response.BankCatalogItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

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

        List<BankCatalogItem> banks = fetchFromVietQr();
        if (!banks.isEmpty()) {
            try {
                redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(banks), CACHE_TTL);
            } catch (Exception e) {
                log.warn("Failed to cache bank list to Redis", e);
            }
        }
        return banks;
    }

    private List<BankCatalogItem> fetchFromVietQr() {
        try {
            ResponseEntity<JsonNode> resp = restTemplate.getForEntity(VIETQR_URL, JsonNode.class);
            JsonNode body = resp.getBody();
            if (body == null || !"00".equals(body.path("code").asText())) {
                log.warn("VietQR returned non-OK response: {}", body);
                return List.of();
            }

            List<BankCatalogItem> result = new ArrayList<>();
            for (JsonNode bank : body.path("data")) {
                int transferSupported = bank.path("transferSupported").asInt(0);
                int lookupSupported = bank.path("lookupSupported").asInt(0);
                // Chỉ lấy ngân hàng hỗ trợ tra cứu tài khoản
                if (lookupSupported == 0) continue;

                result.add(BankCatalogItem.builder()
                        .bankCode(bank.path("code").asText())
                        .bankName(bank.path("name").asText())
                        .bankShortName(bank.path("shortName").asText())
                        .icon(bank.path("logo").asText())
                        .build());
            }
            log.info("Fetched {} banks from VietQR", result.size());
            return result;
        } catch (Exception e) {
            log.error("Failed to fetch bank list from VietQR: {}", e.getMessage());
            return List.of();
        }
    }

    public void evictCache() {
        String cacheKey = appProperties.getRedis().getNamespace() + CACHE_KEY_SUFFIX;
        redisTemplate.delete(cacheKey);
    }
}
