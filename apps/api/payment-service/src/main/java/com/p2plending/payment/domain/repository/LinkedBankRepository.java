package com.p2plending.payment.domain.repository;

import com.p2plending.payment.domain.entity.LinkedBank;
import com.p2plending.payment.domain.enums.WalletOwnerType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LinkedBankRepository extends JpaRepository<LinkedBank, String> {
    List<LinkedBank> findByUserIdAndIsDeletedFalseOrderByIsDefaultDescCreatedAtDesc(String userId);
    List<LinkedBank> findByUserIdAndOwnerTypeAndIsDeletedFalseOrderByIsDefaultDescCreatedAtDesc(
            String userId, WalletOwnerType ownerType);
    Optional<LinkedBank> findByIdAndUserIdAndIsDeletedFalse(String id, String userId);
    Optional<LinkedBank> findByIdAndUserIdAndOwnerTypeAndIsDeletedFalse(
            String id, String userId, WalletOwnerType ownerType);
    Optional<LinkedBank> findByIdAndIsDeletedFalse(String id);
    boolean existsByUserIdAndBankCodeAndBankAccountNoAndIsDeletedFalse(String userId, String bankCode, String bankAccountNo);
    boolean existsByUserIdAndOwnerTypeAndBankCodeAndBankAccountNoAndIsDeletedFalse(
            String userId, WalletOwnerType ownerType, String bankCode, String bankAccountNo);
}
