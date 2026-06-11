package com.p2plending.credit.domain.repository;

import com.p2plending.credit.domain.entity.DocumentAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentAnalysisRepository extends JpaRepository<DocumentAnalysis, String> {
    List<DocumentAnalysis> findByUserIdAndIsDeletedFalseOrderByCreatedAtDesc(String userId);
    List<DocumentAnalysis> findByLoanRequestIdAndIsDeletedFalseOrderByCreatedAtDesc(Long loanRequestId);
}
