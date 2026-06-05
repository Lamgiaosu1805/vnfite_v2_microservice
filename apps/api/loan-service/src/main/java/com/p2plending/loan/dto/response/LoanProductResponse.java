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
    /** Nhóm sản phẩm (1–4) theo biểu lãi suất QĐ-LSGV. */
    private int productGroup;
    /** Sản phẩm ràng buộc theo nghề/đối tượng — app ẩn ô nghề, yêu cầu bằng chứng đối tượng. */
    private boolean professionBound;
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
