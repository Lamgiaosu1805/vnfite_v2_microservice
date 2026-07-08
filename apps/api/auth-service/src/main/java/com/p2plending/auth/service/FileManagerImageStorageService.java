package com.p2plending.auth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * Upload ảnh KYC (CCCD mặt trước/sau, chân dung) lên file-manager VNFITE, trả fileId thật.
 * Active khi APP_IMAGE_STORAGE_PROVIDER=file-manager (live). Mirror loan FileManagerUploadService.
 */
@Service
@ConditionalOnProperty(name = "app.image-storage.provider", havingValue = "file-manager")
@Slf4j
public class FileManagerImageStorageService implements ImageStorageService {

    private static final long MAX_FILE_SIZE = 30L * 1024 * 1024;

    @Value("${file-manager.base-url:https://service.vnfite.com.vn/file-manager/v2}")
    private String fileManagerBaseUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String upload(MultipartFile file) {
        byte[] bytes = validateAndRead(file);

        String originalFileName = (file.getOriginalFilename() == null || file.getOriginalFilename().isBlank())
                ? "kyc-image.jpg"
                : sanitizeFilename(file.getOriginalFilename());

        try {
            HttpHeaders fileHeaders = new HttpHeaders();
            fileHeaders.setContentType(detectMediaType(bytes));

            ByteArrayResource resource = new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return originalFileName;
                }
            };

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("files", new HttpEntity<>(resource, fileHeaders));
            body.add("mappings", objectMapper.writeValueAsString(List.of("Ảnh định danh eKYC")));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            String url = fileManagerBaseUrl + "/upload";
            ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                    url, new HttpEntity<>(body, headers), JsonNode.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                log.warn("Upload ảnh KYC sang file-manager thất bại status={} body={}",
                        response.getStatusCode(), response.getBody());
                throw new IllegalArgumentException("Không thể tải ảnh định danh lên máy chủ. Vui lòng thử lại.");
            }

            String fileId = extractFileId(response.getBody());
            if (fileId == null || fileId.isBlank()) {
                log.warn("File-manager không trả fileId hợp lệ cho ảnh KYC: {}", response.getBody());
                throw new IllegalArgumentException("Không nhận được mã ảnh từ máy chủ. Vui lòng thử lại.");
            }

            log.info("Upload ảnh KYC thành công filename={} → fileId={}", originalFileName, fileId);
            return fileId;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Upload ảnh KYC thất bại filename={} size={}: {}",
                    originalFileName, file.getSize(), e.getMessage(), e);
            throw new IllegalArgumentException("Không thể tải ảnh định danh lên máy chủ. Vui lòng thử lại.");
        }
    }

    private byte[] validateAndRead(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Thiếu ảnh định danh cần tải lên");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("Mỗi ảnh định danh chỉ được tối đa 30MB");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("Không đọc được nội dung ảnh định danh");
        }
        if (detectMediaType(bytes) == null) {
            throw new IllegalArgumentException("Ảnh định danh phải là PNG hoặc JPEG hợp lệ");
        }
        return bytes;
    }

    private MediaType detectMediaType(byte[] bytes) {
        if (bytes == null || bytes.length < 4) {
            return null;
        }
        if (bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 0x50
                && bytes[2] == 0x4E && bytes[3] == 0x47
                && bytes[4] == 0x0D && bytes[5] == 0x0A
                && bytes[6] == 0x1A && bytes[7] == 0x0A) {
            return MediaType.IMAGE_PNG;
        }
        if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return MediaType.IMAGE_JPEG;
        }
        return null;
    }

    private String sanitizeFilename(String filename) {
        String onlyName = filename.replace('\\', '/');
        int lastSlash = onlyName.lastIndexOf('/');
        if (lastSlash >= 0) {
            onlyName = onlyName.substring(lastSlash + 1);
        }
        String sanitized = onlyName.replaceAll("[^A-Za-z0-9._-]", "_");
        return sanitized.isBlank() ? "kyc-image.jpg" : sanitized;
    }

    private String extractFileId(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                String fileId = extractFileId(item);
                if (fileId != null && !fileId.isBlank()) {
                    return fileId;
                }
            }
            return null;
        }
        if (!node.isObject()) {
            return null;
        }
        for (String field : new String[]{"fileId", "file_id", "id", "_id"}) {
            JsonNode value = node.get(field);
            if (value != null && value.isTextual() && !value.asText().isBlank()) {
                return value.asText();
            }
        }
        for (String field : new String[]{"data", "file", "files", "result", "results", "items", "fileIds"}) {
            String fileId = extractFileId(node.get(field));
            if (fileId != null && !fileId.isBlank()) {
                return fileId;
            }
        }
        return null;
    }
}
