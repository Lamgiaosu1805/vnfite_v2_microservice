package com.p2plending.payment.domain.entity;

import com.p2plending.payment.domain.enums.WalletOwnerType;
import com.p2plending.payment.domain.enums.WithdrawalStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "withdrawal_requests")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawalRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, length = 20)
    @Builder.Default
    private WalletOwnerType ownerType = WalletOwnerType.PERSONAL;

    @Column(name = "wallet_id", nullable = false, length = 36)
    private String walletId;

    @Column(name = "linked_bank_id", nullable = false, length = 36)
    private String linkedBankId;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private WithdrawalStatus status = WithdrawalStatus.INITIATED;

    /** transactionId gửi sang TIKLUY — dùng để match callback */
    @Column(name = "transfer_ref", length = 100)
    private String transferRef;

    /** Mã YFCH do TIKLUY sinh, dùng để query trạng thái giao dịch tại MB. */
    @Column(name = "provider_transfer_ref", length = 100)
    private String providerTransferRef;

    /** FT number từ MB Bank, có trong callback thành công */
    @Column(name = "mb_ft_number", length = 50)
    private String mbFtNumber;

    /** ID của WalletTransaction tạo khi FUNDS_LOCKED — để show history */
    @Column(name = "wallet_txn_id", length = 36)
    private String walletTxnId;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "reject_reason", length = 500)
    private String rejectReason;

    /** CMS admin id đã duyệt */
    @Column(name = "approved_by", length = 36)
    private String approvedBy;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0;

    @Column(name = "max_retries", nullable = false)
    @Builder.Default
    private int maxRetries = 3;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private boolean isDeleted = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public boolean canRetry() {
        return retryCount < maxRetries;
    }
}
