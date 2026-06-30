package com.p2plending.loan.domain.repository;

import com.p2plending.loan.domain.entity.FeeRevenueLedger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface FeeRevenueLedgerRepository extends JpaRepository<FeeRevenueLedger, String> {

    boolean existsByLoanIdAndIsDeletedFalse(String loanId);

    Page<FeeRevenueLedger> findByIsDeletedFalseOrderByDisbursedAtDesc(Pageable pageable);

    @Query("SELECT COALESCE(SUM(f.totalFee), 0) FROM FeeRevenueLedger f " +
           "WHERE f.isDeleted = false AND f.disbursedAt >= :from AND f.disbursedAt < :to")
    BigDecimal sumTotalFeeBetween(LocalDateTime from, LocalDateTime to);

    @Query("SELECT COALESCE(SUM(f.appraisalFee), 0) FROM FeeRevenueLedger f WHERE f.isDeleted = false")
    BigDecimal sumAppraisalFee();

    @Query("SELECT COALESCE(SUM(f.vatAmount), 0) FROM FeeRevenueLedger f WHERE f.isDeleted = false")
    BigDecimal sumVatAmount();

    @Query("SELECT COALESCE(SUM(f.totalFee), 0) FROM FeeRevenueLedger f WHERE f.isDeleted = false")
    BigDecimal sumTotalFee();

    long countByIsDeletedFalse();
}
