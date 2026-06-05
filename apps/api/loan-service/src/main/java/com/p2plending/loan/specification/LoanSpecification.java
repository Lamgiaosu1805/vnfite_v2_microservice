package com.p2plending.loan.specification;

import com.p2plending.loan.domain.entity.LoanRequest;
import com.p2plending.loan.dto.request.LoanFilterParams;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Arrays;
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
            if (params.getMinAmount() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("amount"), params.getMinAmount()));
            }
            if (params.getMaxAmount() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("amount"), params.getMaxAmount()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static List<String> provinceSearchTerms(String province) {
        String p = province.trim();
        List<String> terms = new ArrayList<>();
        terms.add(p);

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

        return terms.stream().distinct().toList();
    }
}
