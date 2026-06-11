package com.p2plending.credit.domain.repository;

import com.p2plending.credit.domain.entity.DocumentAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentAnalysisRepository extends JpaRepository<DocumentAnalysis, String> {
    List<DocumentAnalysis> findByUserIdAndIsDeletedFalseOrderByCreatedAtDesc(String userId);
    List<DocumentAnalysis> findByLoanRequestIdAndIsDeletedFalseOrderByCreatedAtDesc(String loanRequestId);
    Optional<DocumentAnalysis> findFirstByLoanRequestIdAndFileIdAndIsDeletedFalseOrderByCreatedAtDesc(
            String loanRequestId, String fileId);
}
