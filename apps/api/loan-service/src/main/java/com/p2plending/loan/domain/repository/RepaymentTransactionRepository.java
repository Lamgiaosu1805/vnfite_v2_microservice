package com.p2plending.loan.domain.repository;

import com.p2plending.loan.domain.entity.RepaymentTransaction;
import com.p2plending.loan.domain.enums.PaymentChannel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface RepaymentTransactionRepository extends JpaRepository<RepaymentTransaction, String> {

    List<RepaymentTransaction> findByLoanIdAndIsDeletedFalseOrderByPaidAtAsc(String loanId);

    boolean existsByLoanIdAndIsDeletedFalse(String loanId);

    boolean existsByScheduleIdAndChannelInAndIsDeletedFalse(String scheduleId, Collection<PaymentChannel> channels);
}
