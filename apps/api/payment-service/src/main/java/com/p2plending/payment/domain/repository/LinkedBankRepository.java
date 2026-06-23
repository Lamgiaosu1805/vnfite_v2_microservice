package com.p2plending.payment.domain.repository;

import com.p2plending.payment.domain.entity.LinkedBank;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LinkedBankRepository extends JpaRepository<LinkedBank, String> {
    List<LinkedBank> findByUserIdAndIsDeletedFalseOrderByIsDefaultDescCreatedAtDesc(String userId);
    Optional<LinkedBank> findByIdAndUserIdAndIsDeletedFalse(String id, String userId);
    Optional<LinkedBank> findByIdAndIsDeletedFalse(String id);
    boolean existsByUserIdAndBankAccountNoAndIsDeletedFalse(String userId, String bankAccountNo);
}
