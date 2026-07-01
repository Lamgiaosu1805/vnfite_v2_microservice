package com.p2plending.loan.domain.repository;

import com.p2plending.loan.domain.entity.EarlySettlement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EarlySettlementRepository extends JpaRepository<EarlySettlement, String> {

    boolean existsByLoanIdAndIsDeletedFalse(String loanId);

    Optional<EarlySettlement> findByLoanIdAndIsDeletedFalse(String loanId);

    Page<EarlySettlement> findAllByIsDeletedFalseOrderBySettledAtDesc(Pageable pageable);
}
