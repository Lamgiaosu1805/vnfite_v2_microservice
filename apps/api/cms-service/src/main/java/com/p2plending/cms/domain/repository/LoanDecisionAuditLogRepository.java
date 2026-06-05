package com.p2plending.cms.domain.repository;

import com.p2plending.cms.domain.entity.LoanDecisionAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface LoanDecisionAuditLogRepository extends JpaRepository<LoanDecisionAuditLog, String> {

    Optional<LoanDecisionAuditLog> findByIdAndIsDeletedFalse(String id);

    @Query("""
        SELECT a FROM LoanDecisionAuditLog a
        WHERE a.isDeleted = false
          AND (:loanId    IS NULL OR a.loanId    = :loanId)
          AND (:decision  IS NULL OR a.decision  = :decision)
          AND (:decidedBy IS NULL OR a.decidedBy = :decidedBy)
          AND (:from      IS NULL OR a.decidedAt >= :from)
          AND (:to        IS NULL OR a.decidedAt <= :to)
        ORDER BY a.decidedAt DESC
    """)
    Page<LoanDecisionAuditLog> findFiltered(
            @Param("loanId")    String loanId,
            @Param("decision")  String decision,
            @Param("decidedBy") String decidedBy,
            @Param("from")      LocalDateTime from,
            @Param("to")        LocalDateTime to,
            Pageable pageable
    );
}
