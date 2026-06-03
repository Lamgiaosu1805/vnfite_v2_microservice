package com.p2plending.cms.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String CACHE_DASHBOARD_STATS = "dashboard_stats";
    public static final String CACHE_DASHBOARD_CHART = "dashboard_chart";

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        // GenericJackson2JsonRedisSerializer() tự tạo ObjectMapper riêng không có JavaTimeModule
        // → LocalDate/LocalDateTime không serialize được → 500. Fix: inject ObjectMapper có JavaTimeModule.
        ObjectMapper cacheMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        var json = new GenericJackson2JsonRedisSerializer(cacheMapper);

        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(json))
                .disableCachingNullValues();

        return RedisCacheManager.builder(factory)
                .cacheDefaults(base)
                .withInitialCacheConfigurations(Map.of(
                        CACHE_DASHBOARD_STATS, base.entryTtl(Duration.ofMinutes(1)),
                        CACHE_DASHBOARD_CHART, base.entryTtl(Duration.ofMinutes(5))
                ))
                .build();
    }
}
