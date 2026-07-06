package com.p2plending.cms.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.p2plending.cms.service.SourceServiceClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/cms/news")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
public class NewsManagementController {

    private final SourceServiceClient sourceServiceClient;

    @GetMapping
    public ResponseEntity<JsonNode> listNews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String type) {
        return ResponseEntity.ok(sourceServiceClient.listNews(page, size, type));
    }

    @GetMapping("/{id}")
    public ResponseEntity<JsonNode> getNews(@PathVariable String id) {
        return ResponseEntity.ok(sourceServiceClient.getNews(id));
    }

    @PostMapping
    public ResponseEntity<JsonNode> createNews(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(sourceServiceClient.createNews(body));
    }

    @PutMapping("/{id}")
    public ResponseEntity<JsonNode> updateNews(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(sourceServiceClient.updateNews(id, body));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteNews(@PathVariable String id) {
        sourceServiceClient.deleteNews(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/images")
    public ResponseEntity<JsonNode> uploadNewsImage(@RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(sourceServiceClient.uploadNewsImage(file));
    }

    @DeleteMapping("/images")
    public ResponseEntity<Void> deleteNewsImage(@RequestParam String url) {
        sourceServiceClient.deleteNewsImage(url);
        return ResponseEntity.noContent().build();
    }
}
