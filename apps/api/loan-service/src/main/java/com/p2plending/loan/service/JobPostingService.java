package com.p2plending.loan.service;

import com.p2plending.loan.domain.entity.JobPosting;
import com.p2plending.loan.domain.repository.JobPostingRepository;
import com.p2plending.loan.dto.request.InternalJobPostingRequest;
import com.p2plending.loan.dto.response.JobImageUploadResponse;
import com.p2plending.loan.dto.response.JobPostingResponse;
import com.p2plending.loan.dto.response.PagedResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobPostingService {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Set<String> ALLOWED_IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");
    private static final long MAX_IMAGE_BYTES = 30L * 1024L * 1024L;
    private static final Set<String> ALLOWED_STATUSES = Set.of("ACTIVE", "INACTIVE");

    private final JobPostingRepository jobPostingRepository;

    @Value("${app.job.image-dir:/var/www/html/images/jobs}")
    private String jobImageDir;

    @Value("${app.job.public-base:http://localhost:7080}")
    private String jobPublicBase;

    @Transactional(readOnly = true)
    public PagedResponse<JobPostingResponse> getPublicList(String industryType, String location, String name, int page, int size) {
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = Math.max(1, Math.min(size, 100));
        Page<JobPosting> result = jobPostingRepository.findByStatusAndIsDeletedFalseOrderByPublishedAtDesc(
                "ACTIVE", PageRequest.of(normalizedPage, normalizedSize * 4));
        String industryFilter = blankToNull(industryType);
        String locationFilter = blankToNull(location);
        String nameFilter = blankToNull(name);
        var filtered = result.getContent().stream()
                .filter(job -> industryFilter == null || industryFilter.equalsIgnoreCase(job.getIndustryType()))
                .filter(job -> locationFilter == null || containsLocation(job.getLocations(), locationFilter))
                .filter(job -> nameFilter == null || containsIgnoreCase(job.getTitle(), nameFilter))
                .limit(normalizedSize)
                .map(job -> JobPostingResponse.summaryFromEntity(job, jobPublicBase))
                .toList();
        return PagedResponse.<JobPostingResponse>builder()
                .content(filtered)
                .page(normalizedPage)
                .size(normalizedSize)
                .totalElements(filtered.size())
                .totalPages(filtered.isEmpty() ? 0 : 1)
                .last(true)
                .build();
    }

    @Transactional(readOnly = true)
    public JobPostingResponse getPublicDetail(String id) {
        JobPosting job = jobPostingRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tin tuyển dụng không tồn tại"));
        if (!"ACTIVE".equals(job.getStatus())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tin tuyển dụng không tồn tại");
        }
        return JobPostingResponse.fromEntity(job, jobPublicBase);
    }

    @Transactional(readOnly = true)
    public PagedResponse<JobPostingResponse> listInternal(String status, int page, int size) {
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = Math.max(1, Math.min(size, 100));
        String normalizedStatus = normalizeStatus(status);
        Page<JobPostingResponse> result = (normalizedStatus == null
                ? jobPostingRepository.findByIsDeletedFalseOrderByPublishedAtDesc(PageRequest.of(normalizedPage, normalizedSize))
                : jobPostingRepository.findByStatusAndIsDeletedFalseOrderByPublishedAtDesc(normalizedStatus, PageRequest.of(normalizedPage, normalizedSize)))
                .map(job -> JobPostingResponse.fromEntity(job, jobPublicBase));
        return PagedResponse.from(result);
    }

    @Transactional(readOnly = true)
    public JobPostingResponse getInternal(String id) {
        JobPosting job = jobPostingRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tin tuyển dụng không tồn tại"));
        return JobPostingResponse.fromEntity(job, jobPublicBase);
    }

    @Transactional
    public JobPostingResponse createJobPosting(InternalJobPostingRequest request) {
        JobPosting job = JobPosting.builder()
                .id(UUID.randomUUID().toString())
                .title(requiredTrim(request.getTitle(), "Tiêu đề không được để trống"))
                .position(blankToNull(request.getPosition()))
                .salary(blankToNull(request.getSalary()))
                .locations(blankToNull(request.getLocations()))
                .industryType(blankToNull(request.getIndustryType()))
                .workingForm(blankToNull(request.getWorkingForm()))
                .experience(blankToNull(request.getExperience()))
                .workModel(blankToNull(request.getWorkModel()))
                .degree(blankToNull(request.getDegree()))
                .description(request.getDescription())
                .imageUrl(blankToNull(request.getImageUrl()))
                .status(normalizeStatusOrDefault(request.getStatus()))
                .publishedAt(request.getPublishedAt() != null
                        ? request.getPublishedAt()
                        : LocalDateTime.now(VIETNAM_ZONE))
                .isDeleted(false)
                .build();
        return JobPostingResponse.fromEntity(jobPostingRepository.save(job), jobPublicBase);
    }

    @Transactional
    public JobPostingResponse updateJobPosting(String id, InternalJobPostingRequest request) {
        JobPosting job = jobPostingRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tin tuyển dụng không tồn tại"));
        job.setTitle(requiredTrim(request.getTitle(), "Tiêu đề không được để trống"));
        job.setPosition(blankToNull(request.getPosition()));
        job.setSalary(blankToNull(request.getSalary()));
        job.setLocations(blankToNull(request.getLocations()));
        job.setIndustryType(blankToNull(request.getIndustryType()));
        job.setWorkingForm(blankToNull(request.getWorkingForm()));
        job.setExperience(blankToNull(request.getExperience()));
        job.setWorkModel(blankToNull(request.getWorkModel()));
        job.setDegree(blankToNull(request.getDegree()));
        job.setDescription(request.getDescription());
        job.setImageUrl(blankToNull(request.getImageUrl()));
        job.setStatus(normalizeStatusOrDefault(request.getStatus()));
        job.setPublishedAt(request.getPublishedAt() != null
                ? request.getPublishedAt()
                : LocalDateTime.now(VIETNAM_ZONE));
        return JobPostingResponse.fromEntity(jobPostingRepository.save(job), jobPublicBase);
    }

    @Transactional
    public void deleteJobPosting(String id) {
        JobPosting job = jobPostingRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tin tuyển dụng không tồn tại"));
        job.setDeleted(true);
        jobPostingRepository.save(job);
    }

    public JobImageUploadResponse uploadJobImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng chọn ảnh");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ảnh tin tuyển dụng tối đa 30MB");
        }
        String extension = fileExtension(file.getOriginalFilename());
        if (!ALLOWED_IMAGE_EXTENSIONS.contains(extension)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Định dạng ảnh không hợp lệ");
        }
        try {
            Path directory = Path.of(jobImageDir).toAbsolutePath().normalize();
            Files.createDirectories(directory);
            String fileName = UUID.randomUUID() + "." + extension;
            Path target = directory.resolve(fileName).normalize();
            if (!target.startsWith(directory)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tên file không hợp lệ");
            }
            file.transferTo(target);
            return JobImageUploadResponse.builder()
                    .url(jobPublicBase.replaceAll("/+$", "") + "/images/jobs/" + fileName)
                    .build();
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Không thể lưu ảnh tin tuyển dụng");
        }
    }

    /**
     * Xóa ảnh tin tuyển dụng mồ côi (đã upload nhưng bài viết bị hủy trước khi lưu).
     * Idempotent — không tồn tại hoặc URL không trỏ vào thư mục ảnh jobs thì bỏ qua, không lỗi.
     */
    public void deleteJobImage(String url) {
        if (url == null || url.isBlank()) return;
        String fileName = url.substring(url.lastIndexOf('/') + 1);
        String extension = fileExtension(fileName);
        if (!ALLOWED_IMAGE_EXTENSIONS.contains(extension) || !fileName.matches("^[a-fA-F0-9-]+\\.[a-zA-Z0-9]+$")) {
            return;
        }
        try {
            Path directory = Path.of(jobImageDir).toAbsolutePath().normalize();
            Path target = directory.resolve(fileName).normalize();
            if (!target.startsWith(directory)) return;
            Files.deleteIfExists(target);
        } catch (IOException ex) {
            log.warn("Không thể xóa ảnh tin tuyển dụng mồ côi {}: {}", fileName, ex.getMessage());
        }
    }

    private boolean containsLocation(String csvLocations, String location) {
        if (csvLocations == null || csvLocations.isBlank()) return false;
        for (String value : csvLocations.split(",")) {
            if (value.trim().equalsIgnoreCase(location)) return true;
        }
        return false;
    }

    private boolean containsIgnoreCase(String haystack, String needle) {
        return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    private String normalizeStatusOrDefault(String value) {
        String normalized = normalizeStatus(value);
        return normalized != null ? normalized : "ACTIVE";
    }

    private String normalizeStatus(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_STATUSES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trạng thái không hợp lệ");
        }
        return normalized;
    }

    private String requiredTrim(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value.trim();
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
