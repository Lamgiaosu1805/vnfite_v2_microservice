package com.p2plending.cms.domain.repository;

import com.p2plending.cms.domain.entity.CmsUser;
import com.p2plending.cms.domain.enums.UserAccountStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CmsUserRepository extends JpaRepository<CmsUser, String> {

    @Query("""
        SELECT u FROM CmsUser u
        WHERE (:kycStatus IS NULL OR u.kycStatus = :kycStatus)
          AND (:role      IS NULL OR u.role      = :role)
          AND (:status    IS NULL OR u.accountStatus = :status)
          AND (:search    IS NULL OR u.email LIKE %:search% OR u.fullName LIKE %:search%)
        """)
    Page<CmsUser> findWithFilters(
            @Param("kycStatus") String kycStatus,
            @Param("role")      String role,
            @Param("status")    UserAccountStatus status,
            @Param("search")    String search,
            Pageable pageable);

    long countByKycStatus(String kycStatus);
    long countByAccountStatus(UserAccountStatus status);
}
