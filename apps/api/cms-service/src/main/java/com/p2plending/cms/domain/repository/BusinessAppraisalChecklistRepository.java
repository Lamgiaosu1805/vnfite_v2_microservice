package com.p2plending.cms.domain.repository;

import com.p2plending.cms.domain.entity.BusinessAppraisalChecklist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BusinessAppraisalChecklistRepository extends JpaRepository<BusinessAppraisalChecklist, String> {
    List<BusinessAppraisalChecklist> findByLoanIdAndIsDeletedFalseOrderByCreatedAtAsc(String loanId);
    Optional<BusinessAppraisalChecklist> findByLoanIdAndChecklistCodeAndIsDeletedFalse(String loanId, String checklistCode);
}
