package com.p2plending.credit.domain.repository;

import com.p2plending.credit.domain.entity.CreditScore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CreditScoreRepository extends JpaRepository<CreditScore, String> {
    Optional<CreditScore> findFirstByUserIdAndStatusAndIsDeletedFalseOrderByCreatedAtDesc(String userId, String status);
    Optional<CreditScore> findFirstByLoanRequestIdAndIsDeletedFalseOrderByCreatedAtDesc(String loanRequestId);
    List<CreditScore> findByUserIdAndStatusAndIsDeletedFalse(String userId, String status);
    List<CreditScore> findByUserIdAndIsDeletedFalseOrderByCreatedAtDesc(String userId);
}
