package com.p2plending.loan.domain.repository;

import com.p2plending.loan.domain.entity.PendingInvestorCredit;
import com.p2plending.loan.domain.enums.PendingCreditStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PendingInvestorCreditRepository extends JpaRepository<PendingInvestorCredit, String> {

    boolean existsByReferenceId(String referenceId);

    List<PendingInvestorCredit> findTop200ByStatusAndIsDeletedFalseOrderByCreatedAtAsc(PendingCreditStatus status);
}
