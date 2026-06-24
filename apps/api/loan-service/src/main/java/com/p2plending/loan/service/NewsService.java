package com.p2plending.loan.service;

import com.p2plending.loan.domain.entity.News;
import com.p2plending.loan.domain.repository.NewsRepository;
import com.p2plending.loan.dto.response.NewsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.text.Normalizer;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class NewsService {

    private final NewsRepository newsRepository;

    @Transactional(readOnly = true)
    public List<NewsResponse> getLatestNews(int limit) {
        int normalizedLimit = Math.max(1, Math.min(limit, 50));
        int candidateLimit = Math.min(normalizedLimit * 4, 200);
        List<News> candidates = newsRepository
                .findByIsDeletedFalseOrderByPublishedAtDesc(PageRequest.of(0, candidateLimit));
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
