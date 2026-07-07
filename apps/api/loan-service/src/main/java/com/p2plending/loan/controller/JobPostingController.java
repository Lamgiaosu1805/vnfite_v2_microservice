package com.p2plending.loan.controller;

import com.p2plending.loan.dto.response.JobPostingResponse;
import com.p2plending.loan.dto.response.PagedResponse;
import com.p2plending.loan.service.JobPostingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobPostingController {

    private final JobPostingService jobPostingService;

    @GetMapping
    public ResponseEntity<PagedResponse<JobPostingResponse>> getJobPostings(
            @RequestParam(required = false) String industryType,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(jobPostingService.getPublicList(industryType, location, name, page, limit));
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobPostingResponse> getJobPostingDetail(@PathVariable String id) {
        return ResponseEntity.ok(jobPostingService.getPublicDetail(id));
    }
}
