package com.p2plending.auth.domain.repository;

import com.p2plending.auth.domain.entity.BusinessProfile;
import com.p2plending.auth.domain.enums.KycStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BusinessProfileRepository extends JpaRepository<BusinessProfile, String> {

    /** Hồ sơ mới nhất của user (kể cả REJECTED) — hiển thị trạng thái trên app. */
    Optional<BusinessProfile> findTopByUserIdAndIsDeletedFalseOrderByCreatedAtDesc(String userId);

    /** Guard: user đã có hồ sơ PENDING/APPROVED thì không cho nộp thêm. */
    boolean existsByUserIdAndStatusInAndIsDeletedFalse(String userId, java.util.Collection<KycStatus> statuses);

    Page<BusinessProfile> findByStatusAndIsDeletedFalse(KycStatus status, Pageable pageable);

    Page<BusinessProfile> findByIsDeletedFalse(Pageable pageable);
}
