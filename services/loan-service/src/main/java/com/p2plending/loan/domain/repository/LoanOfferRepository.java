package com.p2plending.loan.domain.repository;

import com.p2plending.loan.domain.entity.LoanOffer;
import com.p2plending.loan.domain.enums.OfferStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface LoanOfferRepository extends JpaRepository<LoanOffer, Long> {

    List<LoanOffer> findByLoanRequestId(Long loanRequestId);

    List<LoanOffer> findByLoanRequestIdAndStatus(Long loanRequestId, OfferStatus status);

    List<LoanOffer> findByInvestorId(Long investorId);

    boolean existsByLoanRequestIdAndInvestorId(Long loanRequestId, Long investorId);

    @Query("SELECT COALESCE(SUM(o.amount), 0) FROM LoanOffer o " +
           "WHERE o.loanRequestId = :loanRequestId AND o.status = :status")
    BigDecimal sumAmountByLoanRequestIdAndStatus(
            @Param("loanRequestId") Long loanRequestId,
            @Param("status") OfferStatus status);
}
