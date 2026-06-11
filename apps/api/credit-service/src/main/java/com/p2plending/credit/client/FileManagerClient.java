package com.p2plending.credit.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Tải file chứng từ từ file-manager dùng chung của VNFITE
 * (service.vnfite.com.vn/file-manager/v2).
 *
 *   GET /file/{fileId}  → trả về bytes của file
 *
 * App mobile upload chứng từ qua VNFITE API proxy, backend nhận fileId từ
 * file-manager; credit-service chỉ cần fetch lại theo fileId để đưa cho AI phân tích.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FileManagerClient {

    private final RestTemplate restTemplate;

    @Value("${file-manager.base-url:https://service.vnfite.com.vn/file-manager/v2}")
    private String baseUrl;

    /** File tải về kèm content type đã được nhận diện */
    public record FetchedFile(byte[] bytes, String mimeType) {}

    public FetchedFile fetch(String fileId) {
        String url = baseUrl + "/file/" + fileId;
        try {
            ResponseEntity<byte[]> resp = restTemplate.exchange(
                    url, HttpMethod.GET, HttpEntity.EMPTY, byte[].class);

            byte[] body = resp.getBody();
            if (body == null || body.length == 0) {
                throw new IllegalArgumentException("File rỗng từ file-manager: " + fileId);
            }

            String mime = resolveMimeType(resp.getHeaders().getContentType(), body);
            log.info("Tải chứng từ fileId={} ({} bytes, {})", fileId, body.length, mime);
            return new FetchedFile(body, mime);

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Tải file {} từ file-manager thất bại: {}", fileId, e.getMessage());
            throw new IllegalArgumentException("Không tải được chứng từ (fileId=" + fileId + ")");
        }
    }

    /**
     * Ưu tiên Content-Type từ header; nếu thiếu hoặc octet-stream thì đoán theo magic bytes.
     */
    private String resolveMimeType(MediaType contentType, byte[] bytes) {
        if (contentType != null && !MediaType.APPLICATION_OCTET_STREAM.equals(contentType)) {
            String mime = contentType.getType() + "/" + contentType.getSubtype();
            if (mime.startsWith("image/") || "application/pdf".equals(mime)) {
                return mime.toLowerCase();
            }
        }
        return sniffMagicBytes(bytes);
    }

    private String sniffMagicBytes(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (b.length >= 4 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return "image/png";
        }
        if (b.length >= 4 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F') {
            return "application/pdf";
        }
        if (b.length >= 3 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F') {
            return "image/gif";
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "image/webp";
        }
        throw new IllegalArgumentException("Không nhận diện được định dạng file (chỉ hỗ trợ jpeg/png/webp/gif/pdf)");
    }
}
