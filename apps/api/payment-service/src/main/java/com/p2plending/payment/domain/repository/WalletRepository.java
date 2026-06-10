package com.p2plending.payment.domain.repository;

import com.p2plending.payment.domain.entity.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WalletRepository extends JpaRepository<Wallet, String> {
    Optional<Wallet> findByUserIdAndIsDeletedFalse(String userId);
    Optional<Wallet> findByVnfAccountNoAndIsDeletedFalse(String vnfAccountNo);
    boolean existsByUserId(String userId);
}
