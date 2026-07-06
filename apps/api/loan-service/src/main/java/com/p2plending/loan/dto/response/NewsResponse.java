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

    public static NewsResponse fromEntity(News news, String publicBase) {
        return NewsResponse.builder()
                .id(news.getId())
                .title(news.getTitle())
                .subtitle(news.getSubtitle())
                .imageUrl(resolveImageUrl(news.getImageUrl(), publicBase))
                .content(news.getContent())
                .newsType(news.getNewsType())
                .publishedAt(news.getPublishedAt())
                .build();
    }

    public static NewsResponse summaryFromEntity(News news, String publicBase) {
        return NewsResponse.builder()
                .id(news.getId())
                .title(news.getTitle())
                .subtitle(news.getSubtitle())
                .imageUrl(resolveImageUrl(news.getImageUrl(), publicBase))
                .newsType(news.getNewsType())
                .publishedAt(news.getPublishedAt())
                .build();
    }

    /** Tin cũ migrate lưu đường dẫn tương đối (/images/news/x.jpg) — chuẩn hóa về URL tuyệt đối cho mọi client. */
    private static String resolveImageUrl(String imageUrl, String publicBase) {
        if (imageUrl == null || imageUrl.isBlank()) return imageUrl;
        if (imageUrl.startsWith("http://") || imageUrl.startsWith("https://")) return imageUrl;
        String base = publicBase.replaceAll("/+$", "");
        String path = imageUrl.startsWith("/") ? imageUrl : "/" + imageUrl;
        return base + path;
    }
}
