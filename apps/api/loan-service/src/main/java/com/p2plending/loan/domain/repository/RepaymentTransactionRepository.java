package com.p2plending.loan.domain.repository;

import com.p2plending.loan.domain.entity.RepaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RepaymentTransactionRepository extends JpaRepository<RepaymentTransaction, String> {

    List<RepaymentTransaction> findByLoanIdAndIsDeletedFalseOrderByPaidAtAsc(String loanId);
}
