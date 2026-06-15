package com.p2plending.auth.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Public endpoint — không cần JWT.
 * Mobile app gọi khi khởi động để kiểm tra có bắt buộc update lên store không.
 * Cấu hình qua env: APP_MIN_VERSION_ANDROID, APP_MIN_VERSION_IOS,
 *                   APP_STORE_URL_ANDROID, APP_STORE_URL_IOS
 */
@RestController
@RequestMapping("/api/auth")
public class AppVersionController {

    @Value("${app.store.android.min-version}")
    private String androidMinVersion;

    @Value("${app.store.android.store-url}")
    private String androidStoreUrl;

    @Value("${app.store.ios.min-version}")
    private String iosMinVersion;

    @Value("${app.store.ios.store-url}")
    private String iosStoreUrl;

    @GetMapping("/app-version")
    public ResponseEntity<Map<String, Object>> getAppVersion() {
        return ResponseEntity.ok(Map.of(
                "android", Map.of("minVersion", androidMinVersion, "storeUrl", androidStoreUrl),
                "ios",     Map.of("minVersion", iosMinVersion,     "storeUrl", iosStoreUrl)
        ));
    }
}
