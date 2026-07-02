package com.p2plending.payment.domain.repository;

import com.p2plending.payment.domain.entity.Wallet;
import com.p2plending.payment.domain.enums.WalletOwnerType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface WalletRepository extends JpaRepository<Wallet, String> {
    Optional<Wallet> findByUserIdAndIsDeletedFalse(String userId);
    Optional<Wallet> findByUserIdAndOwnerTypeAndIsDeletedFalse(String userId, WalletOwnerType ownerType);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Wallet> findWithLockByUserIdAndIsDeletedFalse(String userId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Wallet> findWithLockByUserIdAndOwnerTypeAndIsDeletedFalse(String userId, WalletOwnerType ownerType);
    Optional<Wallet> findByVnfAccountNoAndIsDeletedFalse(String vnfAccountNo);
    boolean existsByUserId(String userId);
    boolean existsByUserIdAndOwnerTypeAndIsDeletedFalse(String userId, WalletOwnerType ownerType);
}
