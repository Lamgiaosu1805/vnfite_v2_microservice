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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Locale;
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

    @Value("${app.antivirus.enabled:false}")
    private boolean antivirusEnabled;

    @Value("${app.antivirus.host:clamav}")
    private String antivirusHost;

    @Value("${app.antivirus.port:3310}")
    private int antivirusPort;

    public LoanDocumentUploadResponse uploadLoanDocument(MultipartFile file, String label) {
        byte[] bytes = validateAndRead(file);
        scanForVirus(bytes);

        String safeLabel = label == null || label.isBlank() ? "Chứng từ gọi vốn" : label.trim();
        String originalFileName = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()
                ? "chung-tu-goi-von"
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

    private byte[] validateAndRead(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Vui lòng chọn chứng từ cần tải lên");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("Mỗi chứng từ chỉ được tối đa 10MB");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("Không đọc được nội dung chứng từ");
        }
        MediaType detected = detectMediaType(bytes);

        String contentType = file.getContentType();
        if (contentType != null && !contentType.isBlank()) {
            String normalized = contentType.toLowerCase(Locale.ROOT);
            if (!normalized.startsWith("image/") && !MediaType.APPLICATION_PDF_VALUE.equals(normalized)) {
                throw new IllegalArgumentException("Chỉ hỗ trợ tải lên ảnh hoặc PDF");
            }
        }

        if (detected == null) {
            throw new IllegalArgumentException("Chứng từ phải là ảnh PNG/JPEG hoặc PDF hợp lệ");
        }
        return bytes;
    }

    private MediaType detectMediaType(byte[] bytes) {
        if (bytes == null || bytes.length < 4) {
            return null;
        }
        if (bytes.length >= 5
                && bytes[0] == 0x25 && bytes[1] == 0x50
                && bytes[2] == 0x44 && bytes[3] == 0x46
                && bytes[4] == 0x2D) {
            return MediaType.APPLICATION_PDF;
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
        return sanitized.isBlank() ? "chung-tu-goi-von" : sanitized;
    }

    private void scanForVirus(byte[] bytes) {
        if (!antivirusEnabled) {
            return;
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(antivirusHost, antivirusPort), 3_000);
            socket.setSoTimeout(10_000);

            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            out.write("zINSTREAM\0".getBytes(java.nio.charset.StandardCharsets.US_ASCII));

            int offset = 0;
            while (offset < bytes.length) {
                int chunk = Math.min(8192, bytes.length - offset);
                out.write(new byte[] {
                        (byte) (chunk >>> 24),
                        (byte) (chunk >>> 16),
                        (byte) (chunk >>> 8),
                        (byte) chunk
                });
                out.write(bytes, offset, chunk);
                offset += chunk;
            }
            out.write(new byte[] {0, 0, 0, 0});
            out.flush();

            String result = new String(in.readNBytes(512), java.nio.charset.StandardCharsets.UTF_8);
            if (!result.contains("OK")) {
                log.warn("Antivirus rejected uploaded loan document: {}", result.trim());
                throw new IllegalArgumentException("Chứng từ không đạt kiểm tra an toàn. Vui lòng chọn file khác.");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Antivirus scan failed: {}", e.getMessage(), e);
            throw new IllegalArgumentException("Không thể kiểm tra an toàn chứng từ. Vui lòng thử lại.");
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
