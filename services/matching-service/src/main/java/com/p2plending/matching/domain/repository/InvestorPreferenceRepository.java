package com.p2plending.matching.domain.repository;

import com.p2plending.matching.domain.entity.InvestorPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface InvestorPreferenceRepository extends JpaRepository<InvestorPreference, Long> {

    Optional<InvestorPreference> findByInvestorIdAndActiveTrue(Long investorId);

    /**
     * Finds all active investor preferences whose range fully covers the given loan criteria.
     * Hard filters: amount range, interest rate minimum, term range.
     */
    @Query("""
        SELECT p FROM InvestorPreference p
        WHERE p.active = true
          AND p.minInvestmentAmount <= :amount
          AND p.maxInvestmentAmount >= :amount
          AND p.minInterestRate      <= :interestRate
          AND p.minTermMonths        <= :termMonths
          AND p.maxTermMonths        >= :termMonths
        """)
    List<InvestorPreference> findHardMatches(
            @Param("amount")       BigDecimal amount,
            @Param("interestRate") BigDecimal interestRate,
            @Param("termMonths")   Integer    termMonths);

    /**
     * Wider query for soft-scoring: relaxed on amount upper bound (+20%) and term (+3 months).
     */
    @Query("""
        SELECT p FROM InvestorPreference p
        WHERE p.active = true
          AND p.minInvestmentAmount <= :amount
          AND p.maxInvestmentAmount >= :amountFloor
          AND p.minInterestRate      <= :interestRate
          AND p.minTermMonths        <= :termUpper
          AND p.maxTermMonths        >= :termLower
        """)
    List<InvestorPreference> findSoftMatches(
            @Param("amount")      BigDecimal amount,
            @Param("amountFloor") BigDecimal amountFloor,
            @Param("interestRate") BigDecimal interestRate,
            @Param("termLower")   int termLower,
            @Param("termUpper")   int termUpper);
}
