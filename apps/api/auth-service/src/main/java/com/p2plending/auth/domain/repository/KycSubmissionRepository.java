package com.p2plending.auth.domain.repository;

import com.p2plending.auth.domain.entity.KycSubmission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KycSubmissionRepository extends JpaRepository<KycSubmission, String> {

    boolean existsByCccdNumber(String cccdNumber);

    boolean existsByUserIdAndCccdNumber(String userId, String cccdNumber);

    List<KycSubmission> findByUserId(String userId);
}
