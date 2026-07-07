package com.p2plending.loan.service;

import com.p2plending.loan.domain.entity.JobPosting;
import com.p2plending.loan.domain.repository.JobPostingRepository;
import com.p2plending.loan.dto.request.InternalJobPostingRequest;
import com.p2plending.loan.dto.response.JobPostingResponse;
import com.p2plending.loan.dto.response.PagedResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobPostingServiceTest {

    @Mock private JobPostingRepository jobPostingRepository;
    @InjectMocks private JobPostingService jobPostingService;

    @Test
    void filtersPublicListByIndustryAndLocation() {
        ReflectionTestUtils.setField(jobPostingService, "jobPublicBase", "http://localhost:7080");
        LocalDateTime publishedAt = LocalDateTime.of(2026, 7, 6, 9, 0);
        JobPosting itHanoi = job("1", "Backend Developer", "IT", "Hà Nội,TP.HCM", "/images/jobs/1.jpg", publishedAt);
        JobPosting hrDaNang = job("2", "HR Executive", "HR", "Đà Nẵng", "/images/jobs/2.jpg", publishedAt.minusDays(1));
        when(jobPostingRepository.findByStatusAndIsDeletedFalseOrderByPublishedAtDesc(eq("ACTIVE"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(itHanoi, hrDaNang)));

        PagedResponse<JobPostingResponse> result = jobPostingService.getPublicList("IT", "Hà Nội", null, 0, 20);

        assertThat(result.getContent()).extracting(JobPostingResponse::getId).containsExactly("1");
        assertThat(result.getContent().get(0).getImageUrl()).isEqualTo("http://localhost:7080/images/jobs/1.jpg");
        assertThat(result.getContent().get(0).getLocations()).containsExactly("Hà Nội", "TP.HCM");
    }

    @Test
    void createsJobPostingWithDefaultStatusAndPublishedAt() {
        ReflectionTestUtils.setField(jobPostingService, "jobImageDir", "/tmp/jobs");
        ReflectionTestUtils.setField(jobPostingService, "jobPublicBase", "http://localhost:7080");
        InternalJobPostingRequest request = new InternalJobPostingRequest();
        request.setTitle("  Backend Developer  ");
        request.setLocations("Hà Nội");
        when(jobPostingRepository.save(any(JobPosting.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JobPostingResponse result = jobPostingService.createJobPosting(request);

        assertThat(result.getTitle()).isEqualTo("Backend Developer");
        assertThat(result.getStatus()).isEqualTo("ACTIVE");
        assertThat(result.getPublishedAt()).isNotNull();
        verify(jobPostingRepository).save(argThat(job ->
                "Backend Developer".equals(job.getTitle())
                        && "ACTIVE".equals(job.getStatus())
                        && !job.isDeleted()));
    }

    private JobPosting job(String id, String title, String industryType, String locations, String imageUrl, LocalDateTime publishedAt) {
        return JobPosting.builder()
                .id(id)
                .title(title)
                .industryType(industryType)
                .locations(locations)
                .imageUrl(imageUrl)
                .status("ACTIVE")
                .publishedAt(publishedAt)
                .build();
    }
}
