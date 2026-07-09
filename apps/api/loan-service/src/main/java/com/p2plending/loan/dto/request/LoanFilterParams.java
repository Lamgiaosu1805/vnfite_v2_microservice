package com.p2plending.loan.dto.request;

import com.p2plending.loan.domain.enums.LoanStatus;
import com.p2plending.loan.domain.enums.ProductCategory;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.List;

@Data
public class LoanFilterParams {

    private LoanStatus status;
    /** Lọc theo nhiều trạng thái — ưu tiên hơn status khi được set. */
    private List<LoanStatus> statuses;
    /** Lọc các khoản có ít nhất một kỳ trả nợ quá hạn từ 1 ngày trở lên. */
    private boolean overdueOnly;
    private String borrowerId;
    /** Lọc theo tỉnh/thành phố — hỗ trợ dữ liệu mới ở province và dữ liệu cũ còn trong currentAddress. */
    private String province;
    /** Tìm kiếm mã khoản, sản phẩm, mục đích, người gọi vốn/id và thông tin địa chỉ/liên hệ liên quan. */
    private String search;
    /** Lọc theo nhóm sản phẩm: INDIVIDUAL, BUSINESS, ENTERPRISE. */
    private List<ProductCategory> productCategories;
    private BigDecimal minAmount;
    private BigDecimal maxAmount;

    @Min(0)
    private int page = 0;

    @Min(1)
    @Max(100)
    private int size = 20;

    private String sortBy = "createdAt";
    private String sortDir = "desc";

    public Pageable toPageable() {
        Sort sort = "asc".equalsIgnoreCase(sortDir)
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        return PageRequest.of(page, size, sort);
    }

    /** Deterministic cache key for all filter combinations. */
    public String cacheKey() {
        return "%s|%s|%s|%s|%s|%s|%s|%s|%s|%d|%d|%s|%s"
                .formatted(status, statuses, overdueOnly, borrowerId, province, search, productCategories, minAmount, maxAmount, page, size, sortBy, sortDir);
    }
}
