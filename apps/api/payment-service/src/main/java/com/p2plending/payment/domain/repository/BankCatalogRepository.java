package com.p2plending.payment.domain.repository;

import com.p2plending.payment.domain.entity.BankCatalog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BankCatalogRepository extends JpaRepository<BankCatalog, String> {

    List<BankCatalog> findByIsDeletedFalseOrderByBankShortNameAsc();
}
