package com.p2plending.loan.controller;

import com.p2plending.loan.dto.response.LoanProductResponse;
import com.p2plending.loan.service.LoanProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/loans/products")
@RequiredArgsConstructor
public class LoanProductController {

    private final LoanProductService loanProductService;

    /**
     * GET /api/loans/products
     * Trả về danh sách sản phẩm gọi vốn đang hoạt động.
     * Public — không cần JWT.
     */
    @GetMapping
    public ResponseEntity<List<LoanProductResponse>> getProducts() {
        return ResponseEntity.ok(loanProductService.getActiveProducts());
    }
}
