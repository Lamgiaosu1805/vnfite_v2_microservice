package com.p2plending.loan.domain.repository;

import com.p2plending.loan.domain.entity.LoanOffer;
import com.p2plending.loan.domain.enums.OfferStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface LoanOfferRepository extends JpaRepository<LoanOffer, String> {

    List<LoanOffer> findByLoanRequestId(String loanRequestId);

    List<LoanOffer> findByLoanRequestIdAndStatus(String loanRequestId, OfferStatus status);

    List<LoanOffer> findByInvestorId(String investorId);

    boolean existsByLoanRequestIdAndInvestorId(String loanRequestId, String investorId);

    boolean existsByLoanRequestIdAndInvestorIdAndStatusIn(
            String loanRequestId, String investorId, java.util.Collection<OfferStatus> statuses);

    @Query("SELECT COALESCE(SUM(o.amount), 0) FROM LoanOffer o " +
           "WHERE o.loanRequestId = :loanRequestId AND o.status = :status")
    BigDecimal sumAmountByLoanRequestIdAndStatus(
            @Param("loanRequestId") String loanRequestId,
            @Param("status") OfferStatus status);
}
