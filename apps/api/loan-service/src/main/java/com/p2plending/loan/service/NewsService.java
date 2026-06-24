package com.p2plending.loan.service;

import com.p2plending.loan.domain.repository.NewsRepository;
import com.p2plending.loan.dto.response.NewsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NewsService {

    private final NewsRepository newsRepository;

    @Transactional(readOnly = true)
    public List<NewsResponse> getLatestNews(int limit) {
        return newsRepository
                .findByIsDeletedFalseOrderByPublishedAtDesc(PageRequest.of(0, limit))
                .stream()
                .map(NewsResponse::summaryFromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public NewsResponse getNewsDetail(String id) {
        return newsRepository.findByIdAndIsDeletedFalse(id)
                .map(NewsResponse::fromEntity)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tin tức không tồn tại"));
    }
}
