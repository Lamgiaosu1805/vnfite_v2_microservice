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
@RequestMapping("/cms/job-postings")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'HR')")
public class JobPostingManagementController {

    private final SourceServiceClient sourceServiceClient;

    @GetMapping
    public ResponseEntity<JsonNode> listJobPostings(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(sourceServiceClient.listJobPostings(page, size, status));
    }

    @GetMapping("/{id}")
    public ResponseEntity<JsonNode> getJobPosting(@PathVariable String id) {
        return ResponseEntity.ok(sourceServiceClient.getJobPosting(id));
    }

    @PostMapping
    public ResponseEntity<JsonNode> createJobPosting(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(sourceServiceClient.createJobPosting(body));
    }

    @PutMapping("/{id}")
    public ResponseEntity<JsonNode> updateJobPosting(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(sourceServiceClient.updateJobPosting(id, body));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteJobPosting(@PathVariable String id) {
        sourceServiceClient.deleteJobPosting(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/images")
    public ResponseEntity<JsonNode> uploadJobImage(@RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(sourceServiceClient.uploadJobImage(file));
    }

    @DeleteMapping("/images")
    public ResponseEntity<Void> deleteJobImage(@RequestParam String url) {
        sourceServiceClient.deleteJobImage(url);
        return ResponseEntity.noContent().build();
    }
}
