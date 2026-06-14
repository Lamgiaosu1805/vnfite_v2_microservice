package com.p2plending.payment.domain.repository;

import com.p2plending.payment.domain.entity.WalletTransaction;
import com.p2plending.payment.domain.enums.TransactionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, String> {
    Page<WalletTransaction> findByWalletIdAndIsDeletedFalseOrderByCreatedAtDesc(String walletId, Pageable pageable);
    List<WalletTransaction> findByWalletIdAndIsDeletedFalseOrderByCreatedAtAsc(String walletId);
    boolean existsByReferenceId(String referenceId);
    Optional<WalletTransaction> findByExternalRefAndStatus(String externalRef, TransactionStatus status);
}
