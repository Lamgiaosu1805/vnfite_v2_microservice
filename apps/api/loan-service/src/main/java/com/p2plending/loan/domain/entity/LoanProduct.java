package com.p2plending.loan.domain.entity;

import com.p2plending.loan.domain.enums.ProductCategory;
import com.p2plending.loan.domain.enums.RepaymentMethod;
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

    /**
     * Nhóm sản phẩm (1–4) theo biểu lãi suất gọi vốn (QĐ-LSGV). Quyết định biểu
     * lãi suất & phí giải ngân áp dụng theo hạng tín nhiệm. Mặc định nhóm 2 (tiêu dùng).
     */
    @Column(name = "product_group", nullable = false)
    @Builder.Default
    private int productGroup = 2;

    /**
     * Sản phẩm ràng buộc theo nghề/đối tượng (bác sĩ, giáo viên, sinh viên...).
     * Khi true: nghề do sản phẩm xác định, thẩm định cần bằng chứng đúng đối tượng
     * thay vì nhập nghề tự do.
     */
    @Column(name = "profession_bound", nullable = false)
    @Builder.Default
    private boolean professionBound = false;

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

    /** Phí phạt GỐC quá hạn = X% của lãi suất (gốc chưa trả × (X%×rate)/năm × ngày/365). Mặc định 150%. */
    @Column(nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal lateFeeRate = new BigDecimal("150.00");

    /** Phí phạt LÃI quá hạn = X%/năm trên phần lãi chưa trả (lãi chưa trả × X%/năm × ngày/365). Mặc định 10%. */
    @Column(name = "interest_penalty_rate", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal interestPenaltyRate = new BigDecimal("10.00");

    /** Phí tất toán trước hạn = X% trên gốc còn lại (về VNFITE, doanh thu nền tảng). Mặc định 5%. */
    @Column(name = "early_settlement_fee_rate", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal earlySettlementFeeRate = new BigDecimal("5.00");

    /** Kiểu trả nợ áp dụng khi sinh lịch trả nợ lúc khoản vay FUNDED. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    @Builder.Default
    private RepaymentMethod repaymentMethod = RepaymentMethod.EMI_MONTHLY;

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
