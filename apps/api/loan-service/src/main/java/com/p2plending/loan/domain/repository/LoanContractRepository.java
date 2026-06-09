package com.p2plending.loan.domain.repository;

import com.p2plending.loan.domain.entity.LoanContract;
import com.p2plending.loan.domain.enums.ContractType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LoanContractRepository extends JpaRepository<LoanContract, String> {

    Optional<LoanContract> findByIdAndIsDeletedFalse(String id);

    List<LoanContract> findByPartyIdAndIsDeletedFalseOrderByCreatedAtDesc(String partyId);

    List<LoanContract> findByLoanIdAndIsDeletedFalseOrderByCreatedAtDesc(String loanId);

    List<LoanContract> findByPartyIdAndLoanIdAndIsDeletedFalseOrderByCreatedAtDesc(String partyId, String loanId);

    List<LoanContract> findByLoanIdAndContractTypeAndIsDeletedFalse(String loanId, ContractType contractType);

    Optional<LoanContract> findByOfferIdAndIsDeletedFalse(String offerId);
}
