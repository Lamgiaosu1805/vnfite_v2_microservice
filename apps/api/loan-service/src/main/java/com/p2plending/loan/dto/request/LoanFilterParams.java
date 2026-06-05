package com.p2plending.loan.dto.request;

import com.p2plending.loan.domain.enums.LoanStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;

@Data
public class LoanFilterParams {

    private LoanStatus status;
    private String borrowerId;
    /** Lọc theo tỉnh/thành phố — khớp chính xác với giá trị trong cột province. */
    private String province;
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
        return "%s|%s|%s|%s|%s|%d|%d|%s|%s"
                .formatted(status, borrowerId, province, minAmount, maxAmount, page, size, sortBy, sortDir);
    }
}
