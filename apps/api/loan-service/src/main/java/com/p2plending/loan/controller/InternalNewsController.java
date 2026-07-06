package com.p2plending.loan.controller;

import com.p2plending.loan.dto.request.InternalNewsRequest;
import com.p2plending.loan.dto.response.NewsImageUploadResponse;
import com.p2plending.loan.dto.response.NewsResponse;
import com.p2plending.loan.dto.response.PagedResponse;
import com.p2plending.loan.service.NewsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/news")
@RequiredArgsConstructor
public class InternalNewsController {

    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";

    private final NewsService newsService;

    @Value("${app.internal.secret}")
    private String internalSecret;

    @GetMapping
    public ResponseEntity<PagedResponse<NewsResponse>> listNews(
            @RequestHeader(INTERNAL_SECRET_HEADER) String secret,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String type) {
        requireInternalSecret(secret);
        return ResponseEntity.ok(newsService.listInternalNews(type, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<NewsResponse> getNews(
            @RequestHeader(INTERNAL_SECRET_HEADER) String secret,
            @PathVariable String id) {
        requireInternalSecret(secret);
        return ResponseEntity.ok(newsService.getInternalNews(id));
    }

    @PostMapping
    public ResponseEntity<NewsResponse> createNews(
            @RequestHeader(INTERNAL_SECRET_HEADER) String secret,
            @Valid @RequestBody InternalNewsRequest request) {
        requireInternalSecret(secret);
        return ResponseEntity.ok(newsService.createNews(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<NewsResponse> updateNews(
            @RequestHeader(INTERNAL_SECRET_HEADER) String secret,
            @PathVariable String id,
            @Valid @RequestBody InternalNewsRequest request) {
        requireInternalSecret(secret);
        return ResponseEntity.ok(newsService.updateNews(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteNews(
            @RequestHeader(INTERNAL_SECRET_HEADER) String secret,
            @PathVariable String id) {
        requireInternalSecret(secret);
        newsService.deleteNews(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/images")
    public ResponseEntity<NewsImageUploadResponse> uploadImage(
            @RequestHeader(INTERNAL_SECRET_HEADER) String secret,
            @RequestPart("file") MultipartFile file) {
        requireInternalSecret(secret);
        return ResponseEntity.ok(newsService.uploadNewsImage(file));
    }

    @DeleteMapping("/images")
    public ResponseEntity<Void> deleteImage(
            @RequestHeader(INTERNAL_SECRET_HEADER) String secret,
            @RequestParam String url) {
        requireInternalSecret(secret);
        newsService.deleteNewsImage(url);
        return ResponseEntity.noContent().build();
    }

    private void requireInternalSecret(String secret) {
        if (!internalSecret.equals(secret)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid internal secret");
        }
    }
}
