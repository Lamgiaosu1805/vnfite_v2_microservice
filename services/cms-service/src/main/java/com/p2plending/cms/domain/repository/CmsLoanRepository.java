package com.p2plending.cms.domain.repository;

import com.p2plending.cms.domain.entity.CmsLoan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

public interface CmsLoanRepository extends JpaRepository<CmsLoan, String> {

    @Query("""
        SELECT l FROM CmsLoan l
        WHERE (:status     IS NULL OR l.status     = :status)
          AND (:borrowerId IS NULL OR l.borrowerId = :borrowerId)
        """)
    Page<CmsLoan> findWithFilters(
            @Param("status")     String status,
            @Param("borrowerId") String borrowerId,
            Pageable pageable);

    long countByStatus(String status);

    @Query("SELECT COALESCE(SUM(l.amount), 0) FROM CmsLoan l WHERE l.status IN ('FUNDED','REPAYING','COMPLETED')")
    BigDecimal sumFundedVolume();
}
