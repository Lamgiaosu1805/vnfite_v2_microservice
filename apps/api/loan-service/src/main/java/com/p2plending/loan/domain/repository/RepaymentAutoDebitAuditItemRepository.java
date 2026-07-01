package com.p2plending.loan.domain.repository;

import com.p2plending.loan.domain.entity.RepaymentAutoDebitAuditItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RepaymentAutoDebitAuditItemRepository extends JpaRepository<RepaymentAutoDebitAuditItem, String> {
    List<RepaymentAutoDebitAuditItem> findByAuditIdAndIsDeletedFalseOrderByCreatedAtAsc(String auditId);
}
