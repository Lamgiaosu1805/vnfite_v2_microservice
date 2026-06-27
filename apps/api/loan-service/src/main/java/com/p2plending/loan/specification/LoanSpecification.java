package com.p2plending.loan.specification;

import com.p2plending.loan.domain.entity.LoanProduct;
import com.p2plending.loan.domain.entity.LoanRequest;
import com.p2plending.loan.domain.entity.RepaymentSchedule;
import com.p2plending.loan.domain.enums.RepaymentStatus;
import com.p2plending.loan.dto.request.LoanFilterParams;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class LoanSpecification {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private LoanSpecification() {}

    public static Specification<LoanRequest> withFilters(LoanFilterParams params) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (params.getStatuses() != null && !params.getStatuses().isEmpty()) {
                predicates.add(root.get("status").in(params.getStatuses()));
            } else if (params.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), params.getStatus()));
            }
            if (params.getBorrowerId() != null) {
                predicates.add(cb.equal(root.get("borrowerId"), params.getBorrowerId()));
            }
            if (params.getProvince() != null && !params.getProvince().isBlank()) {
                List<Predicate> provincePredicates = provinceSearchTerms(params.getProvince()).stream()
                        .flatMap(term -> {
                            String pattern = "%" + term.toLowerCase() + "%";
                            return Arrays.stream(new Predicate[] {
                                    cb.like(cb.lower(root.get("province")), pattern),
                                    cb.like(cb.lower(root.get("commune")), pattern),
                                    cb.like(cb.lower(root.get("currentAddress")), pattern),
                            });
                        })
                        .toList();
                predicates.add(cb.or(provincePredicates.toArray(new Predicate[0])));
            }
            if (params.getSearch() != null && !params.getSearch().isBlank()) {
                String term = params.getSearch().trim().toLowerCase(Locale.ROOT);
                String pattern = "%" + term + "%";
                List<Predicate> searchPredicates = new ArrayList<>(List.of(
                        cb.like(cb.lower(root.get("id")), pattern),
                        cb.like(cb.lower(root.get("borrowerId")), pattern),
                        cb.like(cb.lower(root.get("purpose")), pattern),
                        cb.like(cb.lower(root.get("referredBy")), pattern),
                        cb.like(cb.lower(root.get("ref1FullName")), pattern),
                        cb.like(cb.lower(root.get("ref1Phone")), pattern),
                        cb.like(cb.lower(root.get("ref1Address")), pattern),
                        cb.like(cb.lower(root.get("ref2FullName")), pattern),
                        cb.like(cb.lower(root.get("ref2Phone")), pattern),
                        cb.like(cb.lower(root.get("ref2Address")), pattern),
                        cb.like(cb.lower(root.get("occupation")), pattern),
                        cb.like(cb.lower(root.get("workplace")), pattern),
                        cb.like(cb.lower(root.get("currentAddress")), pattern),
                        cb.like(cb.lower(root.get("commune")), pattern),
                        cb.like(cb.lower(root.get("province")), pattern),
                        cb.like(cb.lower(root.get("rejectionReason")), pattern),
                        cb.like(cb.lower(root.get("reviewedBy")), pattern)
                ));

                parseLoanSeq(term).ifPresent(seq -> searchPredicates.add(cb.equal(root.get("loanSeq"), seq)));

                Subquery<String> productSubquery = query.subquery(String.class);
                var product = productSubquery.from(LoanProduct.class);
                productSubquery.select(product.get("id"))
                        .where(cb.or(
                                cb.like(cb.lower(product.get("code")), pattern),
                                cb.like(cb.lower(product.get("name")), pattern)
                        ));
                searchPredicates.add(root.get("productId").in(productSubquery));

                predicates.add(cb.or(searchPredicates.toArray(new Predicate[0])));
            }
            if (params.getMinAmount() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("amount"), params.getMinAmount()));
            }
            if (params.getMaxAmount() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("amount"), params.getMaxAmount()));
            }
            if (params.isOverdueOnly()) {
                LocalDate today = LocalDate.now(VIETNAM_ZONE);
                Subquery<String> overdueSubquery = query.subquery(String.class);
                var schedule = overdueSubquery.from(RepaymentSchedule.class);
                overdueSubquery.select(schedule.get("loanId"))
                        .where(cb.and(
                                cb.equal(schedule.get("loanId"), root.get("id")),
                                cb.isFalse(schedule.get("isDeleted")),
                                cb.or(
                                        cb.equal(schedule.get("status"), RepaymentStatus.OVERDUE),
                                        cb.greaterThan(schedule.get("dpd"), 0),
                                        cb.and(
                                                cb.lessThan(schedule.get("dueDate"), today),
                                                cb.notEqual(schedule.get("status"), RepaymentStatus.PAID)
                                        )
                                )
                        ));
                predicates.add(cb.exists(overdueSubquery));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static List<String> provinceSearchTerms(String province) {
        String p = normalizeProvinceInput(province);
        Set<String> terms = new LinkedHashSet<>();
        terms.add(p);
        terms.add(stripVietnameseAccents(p));
        terms.add("TP. " + p);
        terms.add("TP " + p);
        terms.add("Thành phố " + p);
        terms.add("Tinh " + stripVietnameseAccents(p));

        switch (p) {
            case "An Giang" -> terms.add("Kiên Giang");
            case "Bắc Ninh" -> terms.add("Bắc Giang");
            case "Cà Mau" -> terms.add("Bạc Liêu");
            case "Cần Thơ" -> terms.addAll(List.of("Hậu Giang", "Sóc Trăng"));
            case "Đà Nẵng" -> terms.add("Quảng Nam");
            case "Đắk Lắk" -> terms.add("Phú Yên");
            case "Đồng Nai" -> terms.add("Bình Phước");
            case "Đồng Tháp" -> terms.add("Tiền Giang");
            case "Gia Lai" -> terms.add("Bình Định");
            case "Hải Phòng" -> terms.add("Hải Dương");
            case "Hồ Chí Minh" -> terms.addAll(List.of("Bình Dương", "Bà Rịa - Vũng Tàu", "Bà Rịa Vũng Tàu"));
            case "Hưng Yên" -> terms.add("Thái Bình");
            case "Khánh Hòa" -> terms.add("Ninh Thuận");
            case "Lào Cai" -> terms.add("Yên Bái");
            case "Lâm Đồng" -> terms.addAll(List.of("Bình Thuận", "Đắk Nông"));
            case "Ninh Bình" -> terms.addAll(List.of("Hà Nam", "Nam Định"));
            case "Phú Thọ" -> terms.addAll(List.of("Vĩnh Phúc", "Hòa Bình"));
            case "Quảng Ngãi" -> terms.add("Kon Tum");
            case "Quảng Trị" -> terms.add("Quảng Bình");
            case "Tây Ninh" -> terms.add("Long An");
            case "Thái Nguyên" -> terms.add("Bắc Kạn");
            case "Tuyên Quang" -> terms.add("Hà Giang");
            case "Vĩnh Long" -> terms.add("Bến Tre");
            default -> {
            }
        }

        return terms.stream()
                .filter(term -> term != null && !term.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private static java.util.Optional<Long> parseLoanSeq(String term) {
        String digits = term.replaceFirst("(?i)^vnf", "").replaceAll("\\D+", "");
        if (digits.isBlank()) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(Long.parseLong(digits));
        } catch (NumberFormatException ignored) {
            return java.util.Optional.empty();
        }
    }

    private static String normalizeProvinceInput(String province) {
        String p = province.trim();
        return p.replaceFirst("(?iu)^tp\\.?\\s+", "")
                .replaceFirst("(?iu)^thành\\s+phố\\s+", "")
                .replaceFirst("(?iu)^tỉnh\\s+", "")
                .trim();
    }

    private static String stripVietnameseAccents(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return normalized
                .replace('Đ', 'D')
                .replace('đ', 'd')
                .toLowerCase(Locale.ROOT);
    }
}
