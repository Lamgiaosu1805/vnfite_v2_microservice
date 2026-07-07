package com.p2plending.loan.service;

import com.p2plending.loan.domain.entity.JobApplication;
import com.p2plending.loan.domain.entity.JobPosting;
import com.p2plending.loan.domain.repository.JobApplicationRepository;
import com.p2plending.loan.domain.repository.JobPostingRepository;
import com.p2plending.loan.dto.response.JobApplicationResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobApplicationServiceTest {

    @Mock private JobApplicationRepository jobApplicationRepository;
    @Mock private JobPostingRepository jobPostingRepository;
    @InjectMocks private JobApplicationService jobApplicationService;

    @Test
    void submitsApplicationAndStoresCvPrivately(@TempDir Path tempDir) {
        ReflectionTestUtils.setField(jobApplicationService, "jobCvDir", tempDir.toString());
        JobPosting activeJob = JobPosting.builder().id("job-1").title("Backend Developer").status("ACTIVE").build();
        when(jobPostingRepository.findByIdAndIsDeletedFalse("job-1")).thenReturn(Optional.of(activeJob));
        when(jobApplicationRepository.save(any(JobApplication.class))).thenAnswer(invocation -> invocation.getArgument(0));
        MockMultipartFile cv = new MockMultipartFile("file", "resume.pdf", "application/pdf", "dummy".getBytes());

        JobApplicationResponse result = jobApplicationService.submitApplication(
                "job-1", "Nguyễn Văn A", "0912345678", "a@example.com", "Hà Nội", "Xin chào", cv);

        assertThat(result.getFullName()).isEqualTo("Nguyễn Văn A");
        assertThat(result.getJobPostingTitle()).isEqualTo("Backend Developer");
        assertThat(tempDir.toFile().listFiles()).isNotNull().hasSize(1);
    }

    @Test
    void rejectsApplicationWhenJobPostingNotActive() {
        JobPosting inactiveJob = JobPosting.builder().id("job-2").title("HR").status("INACTIVE").build();
        when(jobPostingRepository.findByIdAndIsDeletedFalse("job-2")).thenReturn(Optional.of(inactiveJob));
        MockMultipartFile cv = new MockMultipartFile("file", "resume.pdf", "application/pdf", "dummy".getBytes());

        assertThatThrownBy(() -> jobApplicationService.submitApplication(
                "job-2", "Nguyễn Văn A", "0912345678", null, null, null, cv))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rejectsApplicationWithInvalidCvExtension(@TempDir Path tempDir) {
        ReflectionTestUtils.setField(jobApplicationService, "jobCvDir", tempDir.toString());
        JobPosting activeJob = JobPosting.builder().id("job-1").title("Backend Developer").status("ACTIVE").build();
        when(jobPostingRepository.findByIdAndIsDeletedFalse("job-1")).thenReturn(Optional.of(activeJob));
        MockMultipartFile cv = new MockMultipartFile("file", "resume.exe", "application/octet-stream", "dummy".getBytes());

        assertThatThrownBy(() -> jobApplicationService.submitApplication(
                "job-1", "Nguyễn Văn A", "0912345678", null, null, null, cv))
                .isInstanceOf(ResponseStatusException.class);
    }
}
