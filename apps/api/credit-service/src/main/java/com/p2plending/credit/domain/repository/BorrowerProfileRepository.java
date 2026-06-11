package com.p2plending.credit.domain.repository;

import com.p2plending.credit.domain.entity.BorrowerProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BorrowerProfileRepository extends JpaRepository<BorrowerProfile, String> {
    Optional<BorrowerProfile> findByUserIdAndIsDeletedFalse(String userId);
}
