package com.p2plending.auth.domain.repository;

import com.p2plending.auth.domain.entity.KycSubmission;
import com.p2plending.auth.domain.enums.KycStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface KycSubmissionRepository extends JpaRepository<KycSubmission, String> {

    boolean existsByCccdNumber(String cccdNumber);

    boolean existsByUserIdAndCccdNumber(String userId, String cccdNumber);

    List<KycSubmission> findByUserId(String userId);

    /** Lấy submission APPROVED mới nhất của user — dùng để lấy fullName. */
    Optional<KycSubmission> findTopByUserIdAndStatusOrderByCreatedAtDesc(String userId, KycStatus status);

    /** Lấy submission mới nhất của user — dùng cho CMS xem ảnh eKYC cả khi đang PENDING. */
    Optional<KycSubmission> findTopByUserIdOrderByCreatedAtDesc(String userId);

    @Query("""
            select distinct k.userId
            from KycSubmission k
            where k.isDeleted = false
              and (
                    lower(k.fullName) like lower(concat('%', :search, '%'))
                 or lower(k.cccdNumber) like lower(concat('%', :search, '%'))
              )
            """)
    List<String> findUserIdsByFullNameOrCccd(@Param("search") String search);
}
