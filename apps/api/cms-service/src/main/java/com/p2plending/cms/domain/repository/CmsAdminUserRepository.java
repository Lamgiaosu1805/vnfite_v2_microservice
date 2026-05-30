package com.p2plending.cms.domain.repository;

import com.p2plending.cms.domain.entity.CmsAdminUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CmsAdminUserRepository extends JpaRepository<CmsAdminUser, String> {
    Optional<CmsAdminUser> findByUsernameAndIsDeletedFalse(String username);
    boolean existsByUsername(String username);
}
