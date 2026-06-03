package com.p2plending.loan.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.p2plending.loan.domain.enums.ProductCategory;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoanProductResponse {
    private String id;
    private String code;
    private String name;
    private ProductCategory category;
    private String description;
    private BigDecimal minAmount;
    private BigDecimal maxAmount;
    /** Danh sách kỳ hạn cho phép (tháng): [1, 3, 6, 12] */
    private List<Integer> availableTerms;
    /** URL ảnh đại diện sản phẩm */
    private String imageUrl;
    /** Lãi suất tối đa (%/năm) — null = CMS quyết định */
    private BigDecimal maxInterestRate;
    /** Lãi phạt trả chậm (% của lãi suất hiện tại) */
    private BigDecimal lateFeeRate;
    private int sortOrder;
}
