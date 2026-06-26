package com.p2plending.loan.domain.repository;

import com.p2plending.loan.domain.entity.RepaymentAutoDebitAudit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RepaymentAutoDebitAuditRepository extends JpaRepository<RepaymentAutoDebitAudit, String> {
}
