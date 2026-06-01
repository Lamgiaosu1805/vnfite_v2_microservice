package com.p2plending.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Mock implementation — bỏ qua upload thật, trả về UUID ngẫu nhiên.
 * Thay bằng implementation thật khi deploy production.
 */
@Service
@Profile("!real-storage")
@Slf4j
public class MockImageStorageService implements ImageStorageService {

    @Override
    public String upload(MultipartFile file) {
        String mockId = "mock_" + UUID.randomUUID().toString().replace("-", "");
        log.debug("Mock image upload: filename={} → id={}", file.getOriginalFilename(), mockId);
        return mockId;
    }
}
