package com.p2plending.payment.domain.repository;

import com.p2plending.payment.domain.entity.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface WalletRepository extends JpaRepository<Wallet, String> {
    Optional<Wallet> findByUserIdAndIsDeletedFalse(String userId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Wallet> findWithLockByUserIdAndIsDeletedFalse(String userId);
    Optional<Wallet> findByVnfAccountNoAndIsDeletedFalse(String vnfAccountNo);
    boolean existsByUserId(String userId);
}
