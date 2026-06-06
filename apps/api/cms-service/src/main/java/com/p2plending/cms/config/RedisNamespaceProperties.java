package com.p2plending.cms.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConfigurationProperties(prefix = "app.redis")
public class RedisNamespaceProperties {

    private String namespace = "dev:cms-service";

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String cachePrefix(String cacheName) {
        return normalizedNamespace() + ":" + cacheName + "::";
    }

    private String normalizedNamespace() {
        if (!StringUtils.hasText(namespace)) {
            return "dev:cms-service";
        }
        return namespace.endsWith(":")
                ? namespace.substring(0, namespace.length() - 1)
                : namespace;
    }
}
