package com.p2plending.loan.dto.response;

import com.p2plending.loan.domain.entity.JobPosting;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Getter
@Builder
public class JobPostingResponse {

    private String id;
    private String title;
    private String position;
    private String salary;
    private List<String> locations;
    private String industryType;
    private String workingForm;
    private String experience;
    private String workModel;
    private String degree;
    private String description;
    private String imageUrl;
    private String status;
    private LocalDateTime publishedAt;

    public static JobPostingResponse fromEntity(JobPosting job, String publicBase) {
        return JobPostingResponse.builder()
                .id(job.getId())
                .title(job.getTitle())
                .position(job.getPosition())
                .salary(job.getSalary())
                .locations(splitLocations(job.getLocations()))
                .industryType(job.getIndustryType())
                .workingForm(job.getWorkingForm())
                .experience(job.getExperience())
                .workModel(job.getWorkModel())
                .degree(job.getDegree())
                .description(job.getDescription())
                .imageUrl(resolveImageUrl(job.getImageUrl(), publicBase))
                .status(job.getStatus())
                .publishedAt(job.getPublishedAt())
                .build();
    }

    public static JobPostingResponse summaryFromEntity(JobPosting job, String publicBase) {
        return JobPostingResponse.builder()
                .id(job.getId())
                .title(job.getTitle())
                .position(job.getPosition())
                .salary(job.getSalary())
                .locations(splitLocations(job.getLocations()))
                .industryType(job.getIndustryType())
                .imageUrl(resolveImageUrl(job.getImageUrl(), publicBase))
                .status(job.getStatus())
                .publishedAt(job.getPublishedAt())
                .build();
    }

    private static List<String> splitLocations(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    private static String resolveImageUrl(String imageUrl, String publicBase) {
        if (imageUrl == null || imageUrl.isBlank()) return imageUrl;
        if (imageUrl.startsWith("http://") || imageUrl.startsWith("https://")) return imageUrl;
        String base = publicBase.replaceAll("/+$", "");
        String path = imageUrl.startsWith("/") ? imageUrl : "/" + imageUrl;
        return base + path;
    }
}
