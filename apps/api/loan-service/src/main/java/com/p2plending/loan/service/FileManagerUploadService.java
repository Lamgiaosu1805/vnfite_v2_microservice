package com.p2plending.loan.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2plending.loan.dto.response.LoanDocumentUploadResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileManagerUploadService {

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${file-manager.base-url:https://service.vnfite.com.vn/file-manager/v2}")
    private String fileManagerBaseUrl;

    public LoanDocumentUploadResponse uploadLoanDocument(MultipartFile file, String label) {
        validate(file);

        String safeLabel = label == null || label.isBlank() ? "Chứng từ gọi vốn" : label.trim();
        String originalFileName = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()
                ? "chung-tu-goi-von"
                : file.getOriginalFilename();

        try {
            HttpHeaders fileHeaders = new HttpHeaders();
            fileHeaders.setContentType(resolveMediaType(file.getContentType()));

            ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return originalFileName;
                }
            };

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("files", new HttpEntity<>(resource, fileHeaders));
            body.add("mappings", objectMapper.writeValueAsString(List.of(safeLabel)));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            String url = fileManagerBaseUrl + "/upload";
            ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                    url,
                    new HttpEntity<>(body, headers),
                    JsonNode.class
            );

            if (!response.getStatusCode().is2xxSuccessful()) {
                log.warn("Upload chứng từ sang file-manager thất bại status={} body={}",
                        response.getStatusCode(), response.getBody());
                throw new IllegalArgumentException("Không thể tải chứng từ lên máy chủ. Vui lòng thử lại.");
            }

            String fileId = extractFileId(response.getBody());
            if (fileId == null || fileId.isBlank()) {
                log.warn("File-manager upload không trả fileId hợp lệ: {}", response.getBody());
                throw new IllegalArgumentException("Không nhận được mã file từ máy chủ. Vui lòng thử lại.");
            }

            return LoanDocumentUploadResponse.builder()
                    .fileId(fileId)
                    .fileName(originalFileName)
                    .build();
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Upload chứng từ gọi vốn thất bại filename={} size={}: {}",
                    originalFileName, file.getSize(), e.getMessage(), e);
            throw new IllegalArgumentException("Không thể tải chứng từ lên máy chủ. Vui lòng thử lại.");
        }
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Vui lòng chọn chứng từ cần tải lên");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("Mỗi chứng từ chỉ được tối đa 10MB");
        }

        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            return;
        }

        String normalized = contentType.toLowerCase();
        if (!normalized.startsWith("image/") && !MediaType.APPLICATION_PDF_VALUE.equals(normalized)) {
            throw new IllegalArgumentException("Chỉ hỗ trợ tải lên ảnh hoặc PDF");
        }
    }

    private MediaType resolveMediaType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (Exception ignored) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
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
