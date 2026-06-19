package com.p2plending.loan.service;

import com.p2plending.loan.domain.entity.LoanProduct;
import com.p2plending.loan.domain.repository.LoanProductRepository;
import com.p2plending.loan.dto.request.LoanProductUpdateRequest;
import com.p2plending.loan.dto.response.LoanProductResponse;
import com.p2plending.loan.exception.LoanNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoanProductService {

    private final LoanProductRepository productRepository;

    @Transactional(readOnly = true)
    public List<LoanProductResponse> getActiveProducts() {
        return productRepository.findByIsActiveTrueAndIsDeletedFalseOrderBySortOrderAsc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<LoanProduct> findProductById(String id) {
        return productRepository.findById(id);
    }

    @Transactional
    public LoanProductResponse updateProduct(String id, LoanProductUpdateRequest req) {
        LoanProduct product = productRepository.findById(id)
                .filter(p -> !p.isDeleted())
                .orElseThrow(() -> new LoanNotFoundException("Sản phẩm không tồn tại: " + id));

        product.setName(req.getName());
        product.setDescription(req.getDescription());
        product.setMinAmount(req.getMinAmount());
        product.setMaxAmount(req.getMaxAmount());
        product.setAvailableTerms(
                req.getAvailableTerms().stream()
                        .map(String::valueOf)
                        .reduce((a, b) -> a + "," + b)
                        .orElse(""));
        product.setMaxInterestRate(req.getMaxInterestRate());
        if (req.getLateFeeRate() != null) product.setLateFeeRate(req.getLateFeeRate());
        if (req.getSortOrder() != null) product.setSortOrder(req.getSortOrder());
        if (req.getActive() != null) product.setActive(req.getActive());

        return toResponse(productRepository.save(product));
    }

    @Transactional(readOnly = true)
    public LoanProduct findByCodeOrThrow(String code) {
        return productRepository.findByCodeAndIsDeletedFalse(code)
                .orElseThrow(() -> new LoanNotFoundException(
                        "Sản phẩm gọi vốn không tồn tại hoặc không còn hoạt động: " + code));
    }

    private LoanProductResponse toResponse(LoanProduct p) {
        return LoanProductResponse.builder()
                .id(p.getId())
                .code(p.getCode())
                .name(p.getName())
                .category(p.getCategory())
                .productGroup(p.getProductGroup())
                .professionBound(p.isProfessionBound())
                .description(p.getDescription())
                .minAmount(p.getMinAmount())
                .maxAmount(p.getMaxAmount())
                .availableTerms(p.getAvailableTermList())
                .imageUrl(p.getImageUrl())
                .maxInterestRate(p.getMaxInterestRate())
                .lateFeeRate(p.getLateFeeRate())
                .sortOrder(p.getSortOrder())
                .build();
    }
}
