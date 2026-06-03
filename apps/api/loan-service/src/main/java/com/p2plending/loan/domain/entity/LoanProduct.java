package com.p2plending.loan.domain.entity;

import com.p2plending.loan.domain.enums.ProductCategory;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Entity
@Table(name = "loan_products")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanProduct {

    @Id
    private String id;

    /** Mã sản phẩm duy nhất: FAST, STUDENT, CONSUMER, COSMETIC */
    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ProductCategory category = ProductCategory.INDIVIDUAL;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal minAmount;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal maxAmount;

    /**
     * Danh sách kỳ hạn cho phép (tháng), lưu dạng "1,3,6,12".
     * Dùng {@link #getAvailableTermList()} để lấy List<Integer>.
     */
    @Column(nullable = false, length = 100)
    private String availableTerms;

    /** Lãi suất tối đa (%/năm). NULL = không giới hạn cứng, CMS quyết định. */
    @Column(precision = 5, scale = 2)
    private BigDecimal maxInterestRate;

    /** URL ảnh đại diện sản phẩm hiển thị trên app. */
    @Column(length = 500)
    private String imageUrl;

    /** Lãi phạt trả chậm = X% của lãi suất. Mặc định 150%. */
    @Column(nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal lateFeeRate = new BigDecimal("150.00");

    @Column(nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @Column(nullable = false)
    @Builder.Default
    private boolean isDeleted = false;

    /** Thứ tự hiển thị trên danh sách sản phẩm. */
    @Column(nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    /** Parse chuỗi "1,3,6,12" → List[1, 3, 6, 12] */
    public List<Integer> getAvailableTermList() {
        return Arrays.stream(availableTerms.split(","))
                .map(String::trim)
                .map(Integer::parseInt)
                .toList();
    }

    public boolean isTermAllowed(int termMonths) {
        return getAvailableTermList().contains(termMonths);
    }

    public boolean isAmountInRange(BigDecimal amount) {
        return amount.compareTo(minAmount) >= 0 && amount.compareTo(maxAmount) <= 0;
    }
}
