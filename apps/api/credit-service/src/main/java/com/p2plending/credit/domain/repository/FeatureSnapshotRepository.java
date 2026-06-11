package com.p2plending.credit.domain.repository;

import com.p2plending.credit.domain.entity.FeatureSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeatureSnapshotRepository extends JpaRepository<FeatureSnapshot, String> {
    List<FeatureSnapshot> findByUserIdAndLoanRequestIdAndIsDeletedFalse(String userId, String loanRequestId);
}
