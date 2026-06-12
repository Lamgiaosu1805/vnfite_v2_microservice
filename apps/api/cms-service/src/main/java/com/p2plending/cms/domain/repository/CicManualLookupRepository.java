package com.p2plending.cms.domain.repository;

import com.p2plending.cms.domain.entity.CicManualLookup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CicManualLookupRepository extends JpaRepository<CicManualLookup, String> {

    /** Bản tra CIC mới nhất còn hiệu lực của một khoản — dùng để chấm điểm + hiển thị. */
    Optional<CicManualLookup> findFirstByLoanIdAndIsDeletedFalseOrderByCheckedAtDescCreatedAtDesc(String loanId);
}
