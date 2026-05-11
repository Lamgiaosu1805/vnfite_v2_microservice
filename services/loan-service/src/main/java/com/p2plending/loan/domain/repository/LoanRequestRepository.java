package com.p2plending.loan.domain.repository;

import com.p2plending.loan.domain.entity.LoanRequest;
import com.p2plending.loan.domain.enums.LoanStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LoanRequestRepository
        extends JpaRepository<LoanRequest, String>, JpaSpecificationExecutor<LoanRequest> {

    List<LoanRequest> findByBorrowerId(String borrowerId);

    List<LoanRequest> findByStatus(LoanStatus status);

    @Modifying
    @Query("UPDATE LoanRequest l SET l.status = :status WHERE l.id = :id")
    int updateStatus(@Param("id") String id, @Param("status") LoanStatus status);
}
