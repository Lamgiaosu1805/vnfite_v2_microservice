package com.p2plending.payment.domain.repository;

import com.p2plending.payment.domain.entity.WithdrawalAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WithdrawalAuditLogRepository extends JpaRepository<WithdrawalAuditLog, String> {

    List<WithdrawalAuditLog> findByWithdrawalIdOrderByCreatedAtAsc(String withdrawalId);
}
