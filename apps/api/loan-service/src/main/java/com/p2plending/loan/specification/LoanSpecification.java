package com.p2plending.loan.specification;

import com.p2plending.loan.domain.entity.LoanRequest;
import com.p2plending.loan.dto.request.LoanFilterParams;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class LoanSpecification {

    private LoanSpecification() {}

    public static Specification<LoanRequest> withFilters(LoanFilterParams params) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (params.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), params.getStatus()));
            }
            if (params.getBorrowerId() != null) {
                predicates.add(cb.equal(root.get("borrowerId"), params.getBorrowerId()));
            }
            if (params.getProvince() != null && !params.getProvince().isBlank()) {
                // Tìm theo tỉnh — LIKE để bắt cả "TP. Hồ Chí Minh" và "Hồ Chí Minh"
                predicates.add(cb.like(
                        cb.lower(root.get("province")),
                        "%" + params.getProvince().trim().toLowerCase() + "%"));
            }
            if (params.getMinAmount() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("amount"), params.getMinAmount()));
            }
            if (params.getMaxAmount() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("amount"), params.getMaxAmount()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
