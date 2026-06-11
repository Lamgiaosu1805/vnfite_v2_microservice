package com.p2plending.loan.domain.repository;

import com.p2plending.loan.domain.entity.LoanDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LoanDocumentRepository extends JpaRepository<LoanDocument, String> {
    List<LoanDocument> findByLoanRequestIdAndIsDeletedFalseOrderByCreatedAtAsc(String loanRequestId);
}
