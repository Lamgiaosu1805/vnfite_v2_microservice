package com.p2plending.loan.domain.repository;

import com.p2plending.loan.domain.entity.RepaymentSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RepaymentScheduleRepository extends JpaRepository<RepaymentSchedule, String> {

    List<RepaymentSchedule> findByLoanIdAndIsDeletedFalseOrderByPeriodNumberAsc(String loanId);

    boolean existsByLoanIdAndIsDeletedFalse(String loanId);
}
