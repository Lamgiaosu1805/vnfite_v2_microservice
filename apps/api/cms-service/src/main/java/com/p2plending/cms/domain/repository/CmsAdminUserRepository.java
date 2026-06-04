package com.p2plending.cms.domain.repository;

import com.p2plending.cms.domain.entity.CmsAdminUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CmsAdminUserRepository extends JpaRepository<CmsAdminUser, String> {

    Optional<CmsAdminUser> findByUsernameAndIsDeletedFalse(String username);

    Optional<CmsAdminUser> findByIdAndIsDeletedFalse(String id);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    /** Kiểm tra có admin nào chưa — dùng cho setup endpoint */
    boolean existsByIsDeletedFalse();

    /** Tìm username bắt đầu bằng prefix — dùng để sinh username không trùng */
    @Query("SELECT a.username FROM CmsAdminUser a WHERE a.username LIKE :prefix%")
    List<String> findUsernamesStartingWith(@Param("prefix") String prefix);

    List<CmsAdminUser> findAllByIsDeletedFalseOrderByCreatedAtDesc();
}
