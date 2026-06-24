package com.p2plending.payment.domain.repository;

import com.p2plending.payment.domain.entity.WalletTransaction;
import com.p2plending.payment.domain.enums.TransactionStatus;
import com.p2plending.payment.domain.enums.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, String> {
    Page<WalletTransaction> findByWalletIdAndIsDeletedFalseOrderByCreatedAtDesc(String walletId, Pageable pageable);
    List<WalletTransaction> findByWalletIdAndIsDeletedFalseOrderByCreatedAtAsc(String walletId);
    boolean existsByReferenceId(String referenceId);
    Optional<WalletTransaction> findByExternalRefAndStatus(String externalRef, TransactionStatus status);

    List<WalletTransaction> findByStatusAndTypeAndCreatedAtBeforeAndIsDeletedFalse(
            TransactionStatus status, TransactionType type, LocalDateTime before);

    @Query("""
            SELECT tx
            FROM WalletTransaction tx
            WHERE tx.isDeleted = false
              AND tx.type IN :types
              AND tx.status IN :statuses
              AND (:fromTime IS NULL OR tx.createdAt >= :fromTime)
              AND (:toTime IS NULL OR tx.createdAt < :toTime)
              AND (
                    :search IS NULL
                    OR LOWER(COALESCE(tx.description, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(COALESCE(tx.referenceId, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(COALESCE(tx.externalRef, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(tx.id) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR EXISTS (
                        SELECT wallet.id FROM Wallet wallet
                        WHERE wallet.id = tx.walletId
                          AND wallet.isDeleted = false
                          AND (
                              LOWER(wallet.vnfAccountNo) LIKE LOWER(CONCAT('%', :search, '%'))
                              OR LOWER(wallet.userId) LIKE LOWER(CONCAT('%', :search, '%'))
                          )
                    )
                  )
            ORDER BY tx.createdAt DESC
            """)
    Page<WalletTransaction> findSystemMoneyTransactions(
            @Param("types") List<TransactionType> types,
            @Param("statuses") List<TransactionStatus> statuses,
            @Param("fromTime") LocalDateTime fromTime,
            @Param("toTime") LocalDateTime toTime,
            @Param("search") String search,
            Pageable pageable);
}
