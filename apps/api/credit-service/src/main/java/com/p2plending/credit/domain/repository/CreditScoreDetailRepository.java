package com.p2plending.credit.domain.repository;

import com.p2plending.credit.domain.entity.CreditScoreDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CreditScoreDetailRepository extends JpaRepository<CreditScoreDetail, String> {
    List<CreditScoreDetail> findByCreditScoreIdAndIsDeletedFalse(String creditScoreId);
}
