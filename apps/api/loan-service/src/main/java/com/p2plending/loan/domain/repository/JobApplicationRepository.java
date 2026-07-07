package com.p2plending.loan.domain.repository;

import com.p2plending.loan.domain.entity.JobApplication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JobApplicationRepository extends JpaRepository<JobApplication, String> {

    Page<JobApplication> findByIsDeletedFalseOrderByCreatedAtDesc(Pageable pageable);

    Page<JobApplication> findByJobPostingIdAndIsDeletedFalseOrderByCreatedAtDesc(String jobPostingId, Pageable pageable);

    Optional<JobApplication> findByIdAndIsDeletedFalse(String id);
}
