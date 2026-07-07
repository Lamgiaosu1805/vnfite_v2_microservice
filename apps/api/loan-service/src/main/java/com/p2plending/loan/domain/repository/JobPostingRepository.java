package com.p2plending.loan.domain.repository;

import com.p2plending.loan.domain.entity.JobPosting;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JobPostingRepository extends JpaRepository<JobPosting, String> {

    Page<JobPosting> findByIsDeletedFalseOrderByPublishedAtDesc(Pageable pageable);

    Page<JobPosting> findByStatusAndIsDeletedFalseOrderByPublishedAtDesc(String status, Pageable pageable);

    Optional<JobPosting> findByIdAndIsDeletedFalse(String id);
}
