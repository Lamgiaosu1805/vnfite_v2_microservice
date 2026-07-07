package com.p2plending.loan.controller;

import com.p2plending.loan.dto.response.JobApplicationResponse;
import com.p2plending.loan.service.JobApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** Candidate nộp hồ sơ ứng tuyển — public, không cần đăng nhập. */
@RestController
@RequestMapping("/api/jobs/{jobId}/applications")
@RequiredArgsConstructor
public class JobApplicationController {

    private final JobApplicationService jobApplicationService;

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<JobApplicationResponse> submitApplication(
            @PathVariable String jobId,
            @RequestParam String fullName,
            @RequestParam String phoneNumber,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String introduction,
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(jobApplicationService.submitApplication(
                jobId, fullName, phoneNumber, email, location, introduction, file));
    }
}
