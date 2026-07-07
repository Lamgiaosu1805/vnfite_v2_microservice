package com.p2plending.loan.specification;

import com.p2plending.loan.domain.entity.JobApplication;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class JobApplicationSpecification {

    private JobApplicationSpecification() {}

    public static Specification<JobApplication> withFilters(
            String jobPostingId, String keyword, LocalDate fromDate, LocalDate toDate) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.isFalse(root.get("isDeleted")));

            if (jobPostingId != null && !jobPostingId.isBlank()) {
                predicates.add(cb.equal(root.get("jobPostingId"), jobPostingId));
            }
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("fullName")), pattern),
                        cb.like(cb.lower(root.get("phoneNumber")), pattern),
                        cb.like(cb.lower(root.get("email")), pattern)
                ));
            }
            if (fromDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), fromDate.atStartOfDay()));
            }
            if (toDate != null) {
                LocalDateTime endOfDay = toDate.plusDays(1).atStartOfDay();
                predicates.add(cb.lessThan(root.get("createdAt"), endOfDay));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
