package com.p2plending.loan.domain.repository;

import com.p2plending.loan.domain.entity.RepaymentSchedule;
import com.p2plending.loan.domain.enums.RepaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RepaymentScheduleRepository extends JpaRepository<RepaymentSchedule, String> {

    List<RepaymentSchedule> findByLoanIdAndIsDeletedFalseOrderByPeriodNumberAsc(String loanId);

    boolean existsByLoanIdAndIsDeletedFalse(String loanId);

    List<RepaymentSchedule> findByStatusNotAndIsDeletedFalseOrderByDueDateAscPeriodNumberAsc(
            RepaymentStatus status);
}
