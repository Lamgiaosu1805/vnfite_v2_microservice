package com.p2plending.loan.controller;

import com.p2plending.loan.dto.response.NewsResponse;
import com.p2plending.loan.service.NewsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/news")
@RequiredArgsConstructor
public class NewsController {

    private final NewsService newsService;

    @GetMapping
    public ResponseEntity<List<NewsResponse>> getLatestNews(
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(newsService.getLatestByType(type, Math.min(limit, 50)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<NewsResponse> getNewsDetail(@PathVariable String id) {
        return ResponseEntity.ok(newsService.getNewsDetail(id));
    }
}
