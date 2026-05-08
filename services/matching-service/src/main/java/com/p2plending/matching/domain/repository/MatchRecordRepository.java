package com.p2plending.matching.domain.repository;

import com.p2plending.matching.domain.entity.MatchRecord;
import com.p2plending.matching.domain.enums.MatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MatchRecordRepository extends JpaRepository<MatchRecord, Long> {

    List<MatchRecord> findByLoanIdOrderByScoreDesc(Long loanId);

    List<MatchRecord> findByInvestorId(Long investorId);

    Optional<MatchRecord> findByLoanIdAndInvestorId(Long loanId, Long investorId);

    boolean existsByLoanIdAndInvestorId(Long loanId, Long investorId);

    List<MatchRecord> findByLoanIdAndStatus(Long loanId, MatchStatus status);

    @Modifying
    @Query("UPDATE MatchRecord m SET m.status = 'EXPIRED' WHERE m.loanId = :loanId AND m.status IN ('PENDING','NOTIFIED')")
    int expireByLoanId(@Param("loanId") Long loanId);
}
