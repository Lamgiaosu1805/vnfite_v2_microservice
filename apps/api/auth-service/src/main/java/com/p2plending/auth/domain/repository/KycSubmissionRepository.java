package com.p2plending.auth.domain.repository;

import com.p2plending.auth.domain.entity.KycSubmission;
import com.p2plending.auth.domain.enums.KycStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KycSubmissionRepository extends JpaRepository<KycSubmission, String> {

    boolean existsByCccdNumber(String cccdNumber);

    boolean existsByUserIdAndCccdNumber(String userId, String cccdNumber);

    List<KycSubmission> findByUserId(String userId);

    /** Lấy submission APPROVED mới nhất của user — dùng để lấy fullName. */
    Optional<KycSubmission> findTopByUserIdAndStatusOrderByCreatedAtDesc(String userId, KycStatus status);
}
