package com.p2plending.loan.service;

import com.p2plending.loan.domain.entity.JobApplication;
import com.p2plending.loan.domain.entity.JobPosting;
import com.p2plending.loan.domain.repository.JobApplicationRepository;
import com.p2plending.loan.domain.repository.JobPostingRepository;
import com.p2plending.loan.dto.response.JobApplicationCvResource;
import com.p2plending.loan.dto.response.JobApplicationResponse;
import com.p2plending.loan.dto.response.PagedResponse;
import com.p2plending.loan.specification.JobApplicationSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobApplicationService {

    private static final Set<String> ALLOWED_CV_EXTENSIONS = Set.of("pdf", "doc", "docx");
    private static final long MAX_CV_BYTES = 10L * 1024L * 1024L;

    private final JobApplicationRepository jobApplicationRepository;
    private final JobPostingRepository jobPostingRepository;

    @Value("${app.job.cv-dir:/var/data/job-applications/cv}")
    private String jobCvDir;

    @Transactional
    public JobApplicationResponse submitApplication(String jobPostingId, String fullName, String phoneNumber,
                                                      String email, String location, String introduction,
                                                      MultipartFile cv) {
        JobPosting job = jobPostingRepository.findByIdAndIsDeletedFalse(jobPostingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tin tuyển dụng không tồn tại"));
        if (!"ACTIVE".equals(job.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tin tuyển dụng đã ngừng nhận hồ sơ");
        }
        if (fullName == null || fullName.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Họ tên không được để trống");
        }
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số điện thoại không được để trống");
        }
        if (cv == null || cv.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng đính kèm CV");
        }
        if (cv.getSize() > MAX_CV_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File CV tối đa 10MB");
        }
        String extension = fileExtension(cv.getOriginalFilename());
        if (!ALLOWED_CV_EXTENSIONS.contains(extension)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Định dạng CV không hợp lệ (chỉ nhận PDF/DOC/DOCX)");
        }

        String storedFileName = UUID.randomUUID() + "." + extension;
        try {
            Path directory = Path.of(jobCvDir).toAbsolutePath().normalize();
            Files.createDirectories(directory);
            Path target = directory.resolve(storedFileName).normalize();
            if (!target.startsWith(directory)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tên file không hợp lệ");
            }
            cv.transferTo(target);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Không thể lưu CV");
        }

        JobApplication application = JobApplication.builder()
                .id(UUID.randomUUID().toString())
                .jobPostingId(jobPostingId)
                .fullName(fullName.trim())
                .phoneNumber(phoneNumber.trim())
                .email(blankToNull(email))
                .location(blankToNull(location))
                .introduction(blankToNull(introduction))
                .cvFilePath(storedFileName)
                .cvFileName(cv.getOriginalFilename())
                .isDeleted(false)
                .build();
        JobApplication saved = jobApplicationRepository.save(application);
        return JobApplicationResponse.fromEntity(saved, job.getTitle());
    }

    @Transactional(readOnly = true)
    public PagedResponse<JobApplicationResponse> listApplications(
            String jobPostingId, String keyword, java.time.LocalDate fromDate, java.time.LocalDate toDate,
            int page, int size) {
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = Math.max(1, Math.min(size, 100));
        Pageable pageable = PageRequest.of(normalizedPage, normalizedSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<JobApplication> result = jobApplicationRepository.findAll(
                JobApplicationSpecification.withFilters(jobPostingId, keyword, fromDate, toDate), pageable);
        Page<JobApplicationResponse> mapped = result.map(application -> JobApplicationResponse.fromEntity(
                application,
                jobPostingRepository.findByIdAndIsDeletedFalse(application.getJobPostingId())
                        .map(JobPosting::getTitle)
                        .orElse(null)));
        return PagedResponse.from(mapped);
    }

    @Transactional(readOnly = true)
    public JobApplicationCvResource getCvResource(String id) {
        JobApplication application = jobApplicationRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Hồ sơ ứng tuyển không tồn tại"));
        Path directory = Path.of(jobCvDir).toAbsolutePath().normalize();
        Path target = directory.resolve(application.getCvFilePath()).normalize();
        if (!target.startsWith(directory) || !Files.exists(target)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File CV không tồn tại");
        }
        return JobApplicationCvResource.builder()
                .resource(new FileSystemResource(target))
                .fileName(application.getCvFileName() != null ? application.getCvFileName() : application.getCvFilePath())
                .build();
    }

    @Transactional
    public void deleteApplication(String id) {
        JobApplication application = jobApplicationRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Hồ sơ ứng tuyển không tồn tại"));
        application.setDeleted(true);
        jobApplicationRepository.save(application);
    }

    private String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private String fileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }
}
