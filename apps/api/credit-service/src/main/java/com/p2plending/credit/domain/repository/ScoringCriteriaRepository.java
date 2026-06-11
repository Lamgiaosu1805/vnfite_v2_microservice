package com.p2plending.credit.domain.repository;

import com.p2plending.credit.domain.entity.ScoringCriteria;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScoringCriteriaRepository extends JpaRepository<ScoringCriteria, String> {
    List<ScoringCriteria> findByActiveTrueAndIsDeletedFalseOrderByCriteriaCodeAscPointsDesc();
}
