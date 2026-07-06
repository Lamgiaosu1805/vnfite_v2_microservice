package com.p2plending.loan.domain.repository;

import com.p2plending.loan.domain.entity.News;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NewsRepository extends JpaRepository<News, String> {

    Page<News> findByIsDeletedFalseOrderByPublishedAtDesc(Pageable pageable);

    Page<News> findByNewsTypeAndIsDeletedFalseOrderByPublishedAtDesc(String newsType, Pageable pageable);

    Optional<News> findByIdAndIsDeletedFalse(String id);
}
