package com.p2plending.loan.domain.entity;

import com.p2plending.loan.domain.enums.ContractStatus;
import com.p2plending.loan.domain.enums.ContractType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Hợp đồng điện tử của một khoản gọi vốn.
 *
 * <ul>
 *   <li>{@code INVESTMENT}: 1 hợp đồng cho mỗi offer của nhà đầu tư ({@code offerId} != null).</li>
 *   <li>{@code LOAN_AGREEMENT}: 1 hợp đồng vay cho người gọi vốn ({@code offerId} == null).</li>
 * </ul>
 *
 * Provider hiện là {@code MOCK_VNPT} — sau này thay bằng tích hợp VNPT eContract thật
 * qua {@code ContractSignatureProvider}.
 */
@Entity
@Table(
    name = "loan_contracts",
    indexes = {
        @Index(name = "idx_contract_loan",   columnList = "loanId"),
        @Index(name = "idx_contract_party",  columnList = "partyId"),
        @Index(name = "idx_contract_offer",  columnList = "offerId"),
        @Index(name = "idx_contract_status", columnList = "status")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanContract {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, length = 36)
    private String loanId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ContractType contractType;

    /** userId của bên ký: nhà đầu tư (INVESTMENT) hoặc người gọi vốn (LOAN_AGREEMENT). */
    @Column(nullable = false, length = 36)
    private String partyId;

    /** Offer tương ứng — chỉ có với hợp đồng đầu tư. */
    @Column(length = 36)
    private String offerId;

    /** Mã hợp đồng hiển thị, vd VNF-HD-000123-INV. */
    @Column(length = 60)
    private String contractNo;

    // ── Snapshot điều khoản tại thời điểm phát hành ──
    @Column(precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(precision = 5, scale = 2)
    private BigDecimal interestRate;

    private Integer termMonths;

    // ── Thông tin nhà cung cấp ký số ──
    @Column(nullable = false, length = 30)
    @Builder.Default
    private String provider = "MOCK_VNPT";

    @Column(length = 100)
    private String providerContractId;

    /** URL bản hợp đồng chưa ký (mock). */
    @Column(length = 500)
    private String documentUrl;

    /** URL bản hợp đồng đã ký (mock). */
    @Column(length = 500)
    private String signedDocumentUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ContractStatus status = ContractStatus.PENDING_SIGNATURE;

    private LocalDateTime issuedAt;

    private LocalDateTime signedAt;

    /** Phương thức ký, vd "OTP". */
    @Column(length = 20)
    private String signedVia;

    @Column(nullable = false)
    @Builder.Default
    private boolean isDeleted = false;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
