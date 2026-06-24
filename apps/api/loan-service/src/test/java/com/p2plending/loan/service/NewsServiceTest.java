package com.p2plending.loan.service;

import com.p2plending.loan.domain.entity.News;
import com.p2plending.loan.domain.repository.NewsRepository;
import com.p2plending.loan.dto.response.NewsResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NewsServiceTest {

    @Mock private NewsRepository newsRepository;
    @InjectMocks private NewsService newsService;

    @Test
    void removesLegacyCampaignDuplicatesButKeepsDifferentNews() {
        LocalDateTime publishedAt = LocalDateTime.of(2026, 6, 23, 16, 50);
        News editorial = news("1",
                "VNFITE ký kết hợp tác với Trường Đại học Phenikaa, thúc đẩy phát triển nguồn nhân lực công nghệ tài chính",
                "/images/news/phenikaa-1.jpg", publishedAt);
        News social = news("2",
                "VNFITE KÝ KẾT HỢP TÁC CÙNG TRƯỜNG ĐẠI HỌC PHENIKAA",
                "/images/news/phenikaa-2.jpg", publishedAt.minusMinutes(2));
        News other = news("3", "VNFITE mở rộng hoạt động tại Hải Phòng",
                "/images/news/haiphong.jpg", publishedAt.minusDays(1));
        when(newsRepository.findByIsDeletedFalseOrderByPublishedAtDesc(any(Pageable.class)))
                .thenReturn(List.of(editorial, social, other));

        List<NewsResponse> result = newsService.getLatestNews(6);

        assertThat(result).extracting(NewsResponse::getId).containsExactly("1", "3");
    }

    private News news(String id, String title, String imageUrl, LocalDateTime publishedAt) {
        return News.builder()
                .id(id)
                .title(title)
                .imageUrl(imageUrl)
                .publishedAt(publishedAt)
                .build();
    }
}
