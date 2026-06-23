package com.p2plending.cms.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@RestController
@RequestMapping("/cms/files")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'OPS')")
public class FileProxyController {

    private final RestTemplate restTemplate;

    @Value("${file.manager.base-url}")
    private String fileManagerBaseUrl;

    @GetMapping("/{fileId}")
    public ResponseEntity<byte[]> getFile(@PathVariable String fileId) {
        // Normalize base URL: ensure it ends with /file regardless of how FILE_MANAGER_URL is set
        String base = fileManagerBaseUrl.endsWith("/file")
                ? fileManagerBaseUrl
                : fileManagerBaseUrl.replaceAll("/file/$", "") + "/file";
        String url = base + "/" + fileId;
        try {
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    url, HttpMethod.GET, null, byte[].class);

            HttpHeaders headers = new HttpHeaders();
            MediaType contentType = response.getHeaders().getContentType();
            if (contentType != null) {
                headers.setContentType(contentType);
            } else {
                headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            }
            headers.setCacheControl(CacheControl.maxAge(java.time.Duration.ofHours(1)));

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(response.getBody());
        } catch (HttpClientErrorException e) {
            log.warn("File not found in file-manager: fileId={}, status={}", fileId, e.getStatusCode());
            return ResponseEntity.status(e.getStatusCode()).build();
        } catch (Exception e) {
            log.error("Error proxying file from file-manager: fileId={}", fileId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
