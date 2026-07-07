package com.p2plending.loan.controller;

import com.p2plending.loan.dto.request.InternalJobPostingRequest;
import com.p2plending.loan.dto.response.JobImageUploadResponse;
import com.p2plending.loan.dto.response.JobPostingResponse;
import com.p2plending.loan.dto.response.PagedResponse;
import com.p2plending.loan.service.JobPostingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/job-postings")
@RequiredArgsConstructor
public class InternalJobPostingController {

    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";

    private final JobPostingService jobPostingService;

    @Value("${app.internal.secret}")
    private String internalSecret;

    @GetMapping
    public ResponseEntity<PagedResponse<JobPostingResponse>> listJobPostings(
            @RequestHeader(INTERNAL_SECRET_HEADER) String secret,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        requireInternalSecret(secret);
        return ResponseEntity.ok(jobPostingService.listInternal(status, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobPostingResponse> getJobPosting(
            @RequestHeader(INTERNAL_SECRET_HEADER) String secret,
            @PathVariable String id) {
        requireInternalSecret(secret);
        return ResponseEntity.ok(jobPostingService.getInternal(id));
    }

    @PostMapping
    public ResponseEntity<JobPostingResponse> createJobPosting(
            @RequestHeader(INTERNAL_SECRET_HEADER) String secret,
            @Valid @RequestBody InternalJobPostingRequest request) {
        requireInternalSecret(secret);
        return ResponseEntity.ok(jobPostingService.createJobPosting(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<JobPostingResponse> updateJobPosting(
            @RequestHeader(INTERNAL_SECRET_HEADER) String secret,
            @PathVariable String id,
            @Valid @RequestBody InternalJobPostingRequest request) {
        requireInternalSecret(secret);
        return ResponseEntity.ok(jobPostingService.updateJobPosting(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteJobPosting(
            @RequestHeader(INTERNAL_SECRET_HEADER) String secret,
            @PathVariable String id) {
        requireInternalSecret(secret);
        jobPostingService.deleteJobPosting(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/images")
    public ResponseEntity<JobImageUploadResponse> uploadImage(
            @RequestHeader(INTERNAL_SECRET_HEADER) String secret,
            @RequestPart("file") MultipartFile file) {
        requireInternalSecret(secret);
        return ResponseEntity.ok(jobPostingService.uploadJobImage(file));
    }

    @DeleteMapping("/images")
    public ResponseEntity<Void> deleteImage(
            @RequestHeader(INTERNAL_SECRET_HEADER) String secret,
            @RequestParam String url) {
        requireInternalSecret(secret);
        jobPostingService.deleteJobImage(url);
        return ResponseEntity.noContent().build();
    }

    private void requireInternalSecret(String secret) {
        if (!internalSecret.equals(secret)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid internal secret");
        }
    }
}
