package com.p2plending.loan.domain.repository;

import com.p2plending.loan.domain.entity.LoanProduct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LoanProductRepository extends JpaRepository<LoanProduct, String> {

    Optional<LoanProduct> findByCodeAndIsDeletedFalse(String code);

    List<LoanProduct> findByIsActiveTrueAndIsDeletedFalseOrderBySortOrderAsc();
}
