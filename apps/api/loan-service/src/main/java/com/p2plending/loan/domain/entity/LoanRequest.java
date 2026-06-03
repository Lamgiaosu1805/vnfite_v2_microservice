package com.p2plending.loan.domain.entity;

import com.p2plending.loan.domain.enums.LoanStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "loan_requests",
    indexes = {
        @Index(name = "idx_loan_borrower",  columnList = "borrowerId"),
        @Index(name = "idx_loan_status",    columnList = "status"),
        @Index(name = "idx_loan_created",   columnList = "createdAt")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /** Auto-assigned by MySQL — source for the VNF loan code. Not managed by Hibernate. */
    @Column(unique = true, insertable = false, updatable = false)
    private Long loanSeq;

    /** FK → loan_products.id — nullable để không break dữ liệu cũ trước V4. */
    @Column(length = 36)
    private String productId;

    @Column(nullable = false)
    private String borrowerId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    /** Set by CMS during approval — null until approved. */
    @Column(precision = 5, scale = 2)
    private BigDecimal interestRate;

    @Column(nullable = false)
    private Integer termMonths;

    @Column(nullable = false, length = 500)
    private String purpose;

    /** Người tham chiếu (referrer name or phone). */
    @Column(length = 100)
    private String referredBy;

    // Explicit column names required — Hibernate naming strategy doesn't insert _ between
    // digit and uppercase letter (ref1Address → ref1address, not ref1_address)
    @Column(name = "ref1_full_name", length = 100)
    private String ref1FullName;

    @Column(name = "ref1_relationship", length = 50)
    private String ref1Relationship;

    @Column(name = "ref1_phone", length = 20)
    private String ref1Phone;

    @Column(name = "ref1_address", length = 500)
    private String ref1Address;

    @Column(name = "ref2_full_name", length = 100)
    private String ref2FullName;

    @Column(name = "ref2_relationship", length = 50)
    private String ref2Relationship;

    @Column(name = "ref2_phone", length = 20)
    private String ref2Phone;

    @Column(name = "ref2_address", length = 500)
    private String ref2Address;

    /** Thu nhập hàng tháng (VND). */
    @Column(precision = 15, scale = 2)
    private BigDecimal monthlyIncome;

    /** Nghề nghiệp. */
    @Column(length = 100)
    private String occupation;

    /** Địa chỉ hiện tại chi tiết. */
    @Column(length = 500)
    private String currentAddress;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private LoanStatus status = LoanStatus.PENDING_REVIEW;

    /** Running total of accepted offers — drives FUNDED transition. */
    @Column(nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal fundedAmount = BigDecimal.ZERO;

    /** Lý do từ chối từ CMS. */
    @Column(length = 500)
    private String rejectionReason;

    /** Lý do khách hàng hủy hồ sơ (từ chối điều khoản hoặc tự rút). */
    @Column(length = 500)
    private String borrowerCancelledReason;

    /** Thời điểm CMS duyệt hoặc từ chối. */
    private LocalDateTime reviewedAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean isDeleted = false;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public String getLoanCode() {
        return loanSeq != null ? String.format("VNF%06d", loanSeq) : null;
    }

    public BigDecimal getRemainingAmount() {
        return amount.subtract(fundedAmount);
    }

    public boolean isFullyFunded() {
        return fundedAmount.compareTo(amount) >= 0;
    }
}
