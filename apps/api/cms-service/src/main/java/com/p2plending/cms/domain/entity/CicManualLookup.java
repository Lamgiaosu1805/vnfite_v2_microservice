package com.p2plending.cms.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Kết quả tra cứu CIC nhập tay (giai đoạn chờ API CIC sandbox NĐ94 go-live).
 * Vừa là nguồn chấm điểm nhóm B (credit-service) vừa là audit trail tuân thủ.
 */
@Entity
@Table(name = "cic_manual_lookups")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CicManualLookup {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "loan_id", nullable = false, length = 36)
    private String loanId;

    @Column(name = "borrower_id", length = 36)
    private String borrowerId;

    /** 1-5: nhóm nợ cao nhất hiện tại (B1). Nhóm ≥3 = nợ xấu → cổng loại trừ. */
    @Column(name = "debt_group", nullable = false)
    private Integer debtGroup;

    /** Số ngày quá hạn cao nhất 12 tháng gần nhất (B2). */
    @Column(name = "max_dpd")
    private Integer maxDpd;

    /** Số tổ chức tín dụng đang có dư nợ (B4). */
    @Column(name = "active_lenders")
    private Integer activeLenders;

    @Column(name = "total_outstanding", precision = 15, scale = 2)
    private BigDecimal totalOutstanding;

    @Column(name = "inquiries_recent")
    private Integer inquiriesRecent;

    @Column(name = "checked_at", nullable = false)
    private LocalDate checkedAt;

    @Column(name = "attachment_file_id", length = 100)
    private String attachmentFileId;

    @Column(name = "note", length = 1000)
    private String note;

    @Column(name = "consent_confirmed", nullable = false)
    @Builder.Default
    private boolean consentConfirmed = false;

    @Column(name = "entered_by", nullable = false, length = 100)
    private String enteredBy;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private boolean isDeleted = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
