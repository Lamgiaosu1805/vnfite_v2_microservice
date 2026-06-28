package com.p2plending.loan.domain.repository;

import com.p2plending.loan.domain.entity.InvestorDistributionLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvestorDistributionLogRepository extends JpaRepository<InvestorDistributionLog, String> {

    @Query("""
        SELECT d FROM InvestorDistributionLog d
        WHERE d.isDeleted = false
          AND (:loanId IS NULL OR d.loanId = :loanId)
          AND (:investorId IS NULL OR d.investorId = :investorId)
        ORDER BY d.distributedAt DESC
        """)
    Page<InvestorDistributionLog> findFiltered(
            @Param("loanId") String loanId,
            @Param("investorId") String investorId,
            Pageable pageable);
}
