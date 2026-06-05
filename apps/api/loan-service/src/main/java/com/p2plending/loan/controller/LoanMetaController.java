package com.p2plending.loan.controller;

import com.p2plending.loan.domain.enums.OccupationCategory;
import com.p2plending.loan.dto.response.OccupationOptionResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * Dữ liệu tham chiếu (reference data) cho app gọi vốn. Public — không cần JWT.
 * Mount dưới /api/loans/meta để tránh đụng route /api/loans/{id}.
 */
@RestController
@RequestMapping("/api/loans/meta")
public class LoanMetaController {

    /**
     * GET /api/loans/meta/occupations
     * Danh mục nghề nghiệp chuẩn (theo phân loại ngân hàng) cho dropdown chọn nghề.
     */
    @GetMapping("/occupations")
    public ResponseEntity<List<OccupationOptionResponse>> getOccupations() {
        List<OccupationOptionResponse> options = Arrays.stream(OccupationCategory.values())
                .map(o -> new OccupationOptionResponse(o.name(), o.getLabel()))
                .toList();
        return ResponseEntity.ok(options);
    }
}
