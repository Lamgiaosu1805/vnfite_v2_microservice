package com.p2plending.payment.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "withdrawal_transfer_configs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawalTransferConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "source_label", nullable = false, length = 20)
    private String sourceLabel;

    /** Số tài khoản debit công ty — trước đây hardcode '6966638888' */
    @Column(name = "debit_account", nullable = false, length = 30)
    private String debitAccount;

    @Column(name = "debit_name", nullable = false, length = 100)
    private String debitName;

    /**
     * Template remark gửi MB. Placeholder: {txId}, {accNo}, {accName}.
     * Ví dụ: "YF {txId} {accNo} {accName} RT"
     */
    @Column(name = "remark_template", nullable = false, length = 200)
    private String remarkTemplate;

    /** Ngưỡng tự động duyệt — nếu amount < autoApproveLimit thì không cần CMS duyệt */
    @Column(name = "auto_approve_limit", nullable = false, precision = 15, scale = 2)
    private BigDecimal autoApproveLimit;

    @Column(name = "max_per_txn", nullable = false, precision = 15, scale = 2)
    private BigDecimal maxPerTxn;

    @Column(name = "max_daily_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal maxDailyTotal;

    @Column(name = "max_daily_count", nullable = false)
    private int maxDailyCount;

    @Column(name = "max_retries", nullable = false)
    private int maxRetries;

    /**
     * Danh sách error code không được retry, phân tách bởi dấu phẩy.
     * Ví dụ: "INSUFFICIENT_FUNDS,ACCOUNT_LOCKED"
     * Ops cập nhật trực tiếp trong DB, không cần redeploy.
     */
    @Column(name = "non_retryable_error_codes", nullable = false, length = 500)
    @Builder.Default
    private String nonRetryableErrorCodes = "";

    /** Kiểm tra errorCode có nằm trong danh sách non-retryable không */
    public boolean isNonRetryable(String errorCode) {
        if (errorCode == null || nonRetryableErrorCodes == null || nonRetryableErrorCodes.isBlank()) {
            return false;
        }
        String upper = errorCode.toUpperCase();
        for (String code : nonRetryableErrorCodes.split(",")) {
            if (upper.equals(code.trim().toUpperCase())) return true;
        }
        return false;
    }

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private boolean isDeleted = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public String buildRemark(String txId, String accNo, String accName) {
        return remarkTemplate
                .replace("{txId}", txId)
                .replace("{accNo}", accNo)
                .replace("{accName}", accName != null ? accName : "");
    }
}
