package com.p2plending.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Mock implementation — bỏ qua upload thật, trả về UUID ngẫu nhiên.
 * Active mặc định; trên live đặt APP_IMAGE_STORAGE_PROVIDER=file-manager để dùng bản thật.
 */
@Service
@ConditionalOnProperty(name = "app.image-storage.provider", havingValue = "mock", matchIfMissing = true)
@Slf4j
public class MockImageStorageService implements ImageStorageService {

    @Override
    public String upload(MultipartFile file) {
        String mockId = "mock_" + UUID.randomUUID().toString().replace("-", "");
        log.debug("Mock image upload: filename={} → id={}", file.getOriginalFilename(), mockId);
        return mockId;
    }
}
