package com.p2plending.loan.dto.response;

import com.p2plending.loan.domain.entity.JobApplication;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class JobApplicationResponse {

    private String id;
    private String jobPostingId;
    private String jobPostingTitle;
    private String fullName;
    private String phoneNumber;
    private String email;
    private String location;
    private String introduction;
    private String cvFileName;
    private LocalDateTime createdAt;

    public static JobApplicationResponse fromEntity(JobApplication application, String jobPostingTitle) {
        return JobApplicationResponse.builder()
                .id(application.getId())
                .jobPostingId(application.getJobPostingId())
                .jobPostingTitle(jobPostingTitle)
                .fullName(application.getFullName())
                .phoneNumber(application.getPhoneNumber())
                .email(application.getEmail())
                .location(application.getLocation())
                .introduction(application.getIntroduction())
                .cvFileName(application.getCvFileName())
                .createdAt(application.getCreatedAt())
                .build();
    }
}
