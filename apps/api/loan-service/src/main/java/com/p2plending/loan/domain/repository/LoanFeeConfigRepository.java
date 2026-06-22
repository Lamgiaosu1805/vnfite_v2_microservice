package com.p2plending.loan.domain.repository;

import com.p2plending.loan.domain.entity.LoanFeeConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LoanFeeConfigRepository extends JpaRepository<LoanFeeConfig, Integer> {
    Optional<LoanFeeConfig> findByFeeType(String feeType);
    List<LoanFeeConfig> findAllByIsActiveTrue();
}
