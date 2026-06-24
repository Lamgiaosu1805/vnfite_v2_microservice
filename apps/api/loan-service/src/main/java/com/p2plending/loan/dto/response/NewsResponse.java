package com.p2plending.loan.dto.response;

import com.p2plending.loan.domain.entity.News;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class NewsResponse {

    private String id;
    private String title;
    private String subtitle;
    private String imageUrl;
    private String content;
    private String newsType;
    private LocalDateTime publishedAt;

    public static NewsResponse fromEntity(News news) {
        return NewsResponse.builder()
                .id(news.getId())
                .title(news.getTitle())
                .subtitle(news.getSubtitle())
                .imageUrl(news.getImageUrl())
                .content(news.getContent())
                .newsType(news.getNewsType())
                .publishedAt(news.getPublishedAt())
                .build();
    }

    public static NewsResponse summaryFromEntity(News news) {
        return NewsResponse.builder()
                .id(news.getId())
                .title(news.getTitle())
                .subtitle(news.getSubtitle())
                .imageUrl(news.getImageUrl())
                .newsType(news.getNewsType())
                .publishedAt(news.getPublishedAt())
                .build();
    }
}
