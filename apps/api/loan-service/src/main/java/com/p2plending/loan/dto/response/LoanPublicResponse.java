package com.p2plending.loan.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.p2plending.loan.domain.enums.LoanStatus;
import com.p2plending.loan.domain.enums.ProductCategory;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoanPublicResponse {
    private String id;
    private String loanCode;
    private String productId;
    private String productCode;
    private String productName;
    /** INDIVIDUAL | BUSINESS | ENTERPRISE — dùng để lọc/hiển thị badge trên app. */
    private ProductCategory productCategory;
    private BigDecimal amount;
    private BigDecimal interestRate;
    private BigDecimal proposedAmount;
    private BigDecimal proposedInterestRate;
    private Integer termMonths;
    private String purpose;
    private String occupation;
    private String workplace;
    private String workplaceAddress;
    private String province;
    private LoanStatus status;
    private BigDecimal fundedAmount;
    private BigDecimal remainingAmount;
    private BigDecimal appraisalFee;
    private BigDecimal vatAmount;
    private BigDecimal totalFee;
    private BigDecimal netDisbursement;
    private LocalDateTime reviewedAt;
    private LocalDateTime activatedAt;
    private LocalDateTime listedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Thông tin người gọi vốn (đã che một phần để bảo vệ thông tin cá nhân)
    private String borrowerFullName;
    private String borrowerPhone;
    private String borrowerCccd;
    /** Tên doanh nghiệp/hộ kinh doanh — chỉ có khi productCategory là BUSINESS/ENTERPRISE và hồ sơ DN đã duyệt.
     * App hiển thị tên này thay cho borrowerFullName (tên chủ) khi gọi vốn theo tư cách pháp nhân. */
    private String businessName;

    // Người tham chiếu 1
    private String ref1FullName;
    private String ref1Relationship;
    private String ref1Phone;
    private String ref1Address;

    // Người tham chiếu 2
    private String ref2FullName;
    private String ref2Relationship;
    private String ref2Phone;
    private String ref2Address;
}
