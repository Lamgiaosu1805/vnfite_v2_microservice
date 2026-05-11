package com.p2plending.matching.domain.repository;

import com.p2plending.matching.domain.entity.PendingLoan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PendingLoanRepository extends JpaRepository<PendingLoan, String> {

    /** Loans still open for matching (not yet fully funded). */
    @Query("SELECT l FROM PendingLoan l WHERE l.fullyFunded = false ORDER BY l.lastMatchedAt ASC NULLS FIRST")
    List<PendingLoan> findUnfundedOrderByLastMatched();
}
