package com.p2plending.loan.controller;

import com.p2plending.loan.dto.response.JobApplicationCvResource;
import com.p2plending.loan.dto.response.JobApplicationResponse;
import com.p2plending.loan.dto.response.PagedResponse;
import com.p2plending.loan.service.JobApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@RestController
@RequestMapping("/internal/job-applications")
@RequiredArgsConstructor
public class InternalJobApplicationController {

    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";

    private final JobApplicationService jobApplicationService;

    @Value("${app.internal.secret}")
    private String internalSecret;

    @GetMapping
    public ResponseEntity<PagedResponse<JobApplicationResponse>> listApplications(
            @RequestHeader(INTERNAL_SECRET_HEADER) String secret,
            @RequestParam(required = false) String jobPostingId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireInternalSecret(secret);
        return ResponseEntity.ok(jobApplicationService.listApplications(jobPostingId, keyword, fromDate, toDate, page, size));
    }

    @GetMapping("/{id}/cv")
    public ResponseEntity<Resource> downloadCv(
            @RequestHeader(INTERNAL_SECRET_HEADER) String secret,
            @PathVariable String id) {
        requireInternalSecret(secret);
        JobApplicationCvResource cv = jobApplicationService.getCvResource(id);
        String encodedFileName = URLEncoder.encode(cv.getFileName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFileName)
                .body(cv.getResource());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteApplication(
            @RequestHeader(INTERNAL_SECRET_HEADER) String secret,
            @PathVariable String id) {
        requireInternalSecret(secret);
        jobApplicationService.deleteApplication(id);
        return ResponseEntity.noContent().build();
    }

    private void requireInternalSecret(String secret) {
        if (!internalSecret.equals(secret)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid internal secret");
        }
    }
}
