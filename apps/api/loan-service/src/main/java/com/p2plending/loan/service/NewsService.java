package com.p2plending.loan.service;

import com.p2plending.loan.domain.entity.News;
import com.p2plending.loan.domain.repository.NewsRepository;
import com.p2plending.loan.dto.request.InternalNewsRequest;
import com.p2plending.loan.dto.response.NewsImageUploadResponse;
import com.p2plending.loan.dto.response.PagedResponse;
import com.p2plending.loan.dto.response.NewsResponse;
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
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.text.Normalizer;
import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NewsService {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Set<String> ALLOWED_IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");
    private static final long MAX_IMAGE_BYTES = 10L * 1024L * 1024L;

    private final NewsRepository newsRepository;

    @Value("${app.news.image-dir:/var/www/html/images/news}")
    private String newsImageDir;

    @Value("${app.news.public-base:http://localhost:7080}")
    private String newsPublicBase;

    @Transactional(readOnly = true)
    public List<NewsResponse> getLatestNews(int limit) {
        return getLatestByType(null, limit);
    }

    @Transactional(readOnly = true)
    public List<NewsResponse> getLatestByType(String type, int limit) {
        int normalizedLimit = Math.max(1, Math.min(limit, 50));
        int candidateLimit = Math.min(normalizedLimit * 4, 200);
        String normalizedType = normalizeNewsType(type);
        Page<News> page = normalizedType == null
                ? newsRepository.findByIsDeletedFalseOrderByPublishedAtDesc(PageRequest.of(0, candidateLimit))
                : newsRepository.findByNewsTypeAndIsDeletedFalseOrderByPublishedAtDesc(normalizedType, PageRequest.of(0, candidateLimit));
        List<News> candidates = page.getContent();
        List<News> unique = new ArrayList<>();
        for (News candidate : candidates) {
            boolean duplicate = unique.stream().anyMatch(existing -> isNearDuplicate(existing, candidate));
            if (!duplicate) unique.add(candidate);
            if (unique.size() >= normalizedLimit) break;
        }
        return unique.stream().map(NewsResponse::summaryFromEntity).toList();
    }

    @Transactional(readOnly = true)
    public NewsResponse getNewsDetail(String id) {
        return newsRepository.findByIdAndIsDeletedFalse(id)
                .map(NewsResponse::fromEntity)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tin tức không tồn tại"));
    }

    @Transactional(readOnly = true)
    public PagedResponse<NewsResponse> listInternalNews(String type, int page, int size) {
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = Math.max(1, Math.min(size, 100));
        String normalizedType = normalizeNewsType(type);
        Page<NewsResponse> result = (normalizedType == null
                ? newsRepository.findByIsDeletedFalseOrderByPublishedAtDesc(PageRequest.of(normalizedPage, normalizedSize))
                : newsRepository.findByNewsTypeAndIsDeletedFalseOrderByPublishedAtDesc(normalizedType, PageRequest.of(normalizedPage, normalizedSize)))
                .map(NewsResponse::fromEntity);
        return PagedResponse.from(result);
    }

    @Transactional(readOnly = true)
    public NewsResponse getInternalNews(String id) {
        return getNewsDetail(id);
    }

    @Transactional
    public NewsResponse createNews(InternalNewsRequest request) {
        News news = News.builder()
                .id(UUID.randomUUID().toString())
                .title(requiredTrim(request.getTitle(), "Tiêu đề không được để trống"))
                .subtitle(blankToNull(request.getSubtitle()))
                .imageUrl(blankToNull(request.getImageUrl()))
                .content(request.getContent())
                .newsType(normalizeNewsTypeOrDefault(request.getNewsType()))
                .publishedAt(request.getPublishedAt() != null
                        ? request.getPublishedAt()
                        : LocalDateTime.now(VIETNAM_ZONE))
                .isDeleted(false)
                .build();
        return NewsResponse.fromEntity(newsRepository.save(news));
    }

    @Transactional
    public NewsResponse updateNews(String id, InternalNewsRequest request) {
        News news = newsRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tin tức không tồn tại"));
        news.setTitle(requiredTrim(request.getTitle(), "Tiêu đề không được để trống"));
        news.setSubtitle(blankToNull(request.getSubtitle()));
        news.setImageUrl(blankToNull(request.getImageUrl()));
        news.setContent(request.getContent());
        news.setNewsType(normalizeNewsTypeOrDefault(request.getNewsType()));
        news.setPublishedAt(request.getPublishedAt() != null
                ? request.getPublishedAt()
                : LocalDateTime.now(VIETNAM_ZONE));
        return NewsResponse.fromEntity(newsRepository.save(news));
    }

    @Transactional
    public void deleteNews(String id) {
        News news = newsRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tin tức không tồn tại"));
        news.setDeleted(true);
        newsRepository.save(news);
    }

    public NewsImageUploadResponse uploadNewsImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng chọn ảnh");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ảnh tin tức tối đa 10MB");
        }

        String extension = imageExtension(file.getOriginalFilename());
        if (!ALLOWED_IMAGE_EXTENSIONS.contains(extension)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Định dạng ảnh không hợp lệ");
        }

        try {
            Path directory = Path.of(newsImageDir).toAbsolutePath().normalize();
            Files.createDirectories(directory);
            String fileName = UUID.randomUUID() + "." + extension;
            Path target = directory.resolve(fileName).normalize();
            if (!target.startsWith(directory)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tên file không hợp lệ");
            }
            file.transferTo(target);
            return NewsImageUploadResponse.builder()
                    .url(newsPublicBase.replaceAll("/+$", "") + "/images/news/" + fileName)
                    .build();
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Không thể lưu ảnh tin tức");
        }
    }

    /**
     * Xóa ảnh tin tức mồ côi (đã upload nhưng bài viết bị hủy trước khi lưu).
     * Idempotent — không tồn tại hoặc URL không trỏ vào thư mục ảnh tin tức thì bỏ qua, không lỗi.
     */
    public void deleteNewsImage(String url) {
        if (url == null || url.isBlank()) return;
        String fileName = url.substring(url.lastIndexOf('/') + 1);
        String extension = imageExtension(fileName);
        if (!ALLOWED_IMAGE_EXTENSIONS.contains(extension) || !fileName.matches("^[a-fA-F0-9-]+\\.[a-zA-Z0-9]+$")) {
            return;
        }
        try {
            Path directory = Path.of(newsImageDir).toAbsolutePath().normalize();
            Path target = directory.resolve(fileName).normalize();
            if (!target.startsWith(directory)) return;
            Files.deleteIfExists(target);
        } catch (IOException ex) {
            log.warn("Không thể xóa ảnh tin tức mồ côi {}: {}", fileName, ex.getMessage());
        }
    }

    private String normalizeNewsTypeOrDefault(String value) {
        String normalized = normalizeNewsType(value);
        return normalized != null ? normalized : "NORMAL";
    }

    private String normalizeNewsType(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("NORMAL", "FEATURED").contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Loại tin không hợp lệ");
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

    private String imageExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private boolean isNearDuplicate(News first, News second) {
        if (!isSamePublishingWindow(first, second)) return false;
        if (first.getImageUrl() != null && !first.getImageUrl().isBlank()
                && first.getImageUrl().equalsIgnoreCase(second.getImageUrl())) {
            return true;
        }

        Set<String> firstTokens = titleTokens(first.getTitle());
        Set<String> secondTokens = titleTokens(second.getTitle());
        if (firstTokens.isEmpty() || secondTokens.isEmpty()) return false;
        Set<String> intersection = new HashSet<>(firstTokens);
        intersection.retainAll(secondTokens);
        double overlap = (double) intersection.size() / Math.min(firstTokens.size(), secondTokens.size());
        return overlap >= 0.72d;
    }

    private boolean isSamePublishingWindow(News first, News second) {
        if (first.getPublishedAt() == null || second.getPublishedAt() == null) return true;
        long days = Math.abs(Duration.between(first.getPublishedAt(), second.getPublishedAt()).toDays());
        return days <= 7;
    }

    private Set<String> titleTokens(String value) {
        if (value == null) return Set.of();
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
        if (normalized.isEmpty()) return Set.of();
        Set<String> tokens = new HashSet<>();
        for (String token : normalized.split("\\s+")) {
            if (token.length() >= 2) tokens.add(token);
        }
        return tokens;
    }
}
