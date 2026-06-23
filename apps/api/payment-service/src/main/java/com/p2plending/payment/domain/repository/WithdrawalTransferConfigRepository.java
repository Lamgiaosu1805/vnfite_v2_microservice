package com.p2plending.payment.domain.repository;

import com.p2plending.payment.domain.entity.WithdrawalTransferConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WithdrawalTransferConfigRepository extends JpaRepository<WithdrawalTransferConfig, Integer> {

    Optional<WithdrawalTransferConfig> findFirstByActiveTrueAndIsDeletedFalse();
}
