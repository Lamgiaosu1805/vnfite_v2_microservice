package com.p2plending.payment.dto.response;

import com.p2plending.payment.domain.entity.WithdrawalRequest;
import com.p2plending.payment.domain.enums.WithdrawalStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class WithdrawalResponse {

    private String id;
    private String userId;
    /** Số điện thoại khách hàng — chỉ có trong monitoring endpoint (enriched từ auth-service) */
    private String customerPhone;
    /** Họ tên khách hàng — chỉ có trong monitoring endpoint */
    private String customerName;
    private BigDecimal amount;
    private WithdrawalStatus status;
    private String statusLabel;
    private String bankName;
    private String bankAccountNo;
    private String transferRef;
    private String mbFtNumber;
    private String providerTransferRef;
    private String rejectReason;
    private String failureReason;
    private int retryCount;
    private int maxRetries;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static WithdrawalResponse from(WithdrawalRequest wr,
                                          String bankName, String bankAccountNo) {
        return WithdrawalResponse.builder()
                .id(wr.getId())
                .userId(wr.getUserId())
                .amount(wr.getAmount())
                .status(wr.getStatus())
                .statusLabel(toLabel(wr.getStatus()))
                .bankName(bankName)
                .bankAccountNo(bankAccountNo)
                .transferRef(wr.getTransferRef())
                .mbFtNumber(wr.getMbFtNumber())
                .providerTransferRef(wr.getProviderTransferRef())
                .rejectReason(wr.getRejectReason())
                .failureReason(wr.getFailureReason())
                .retryCount(wr.getRetryCount())
                .maxRetries(wr.getMaxRetries())
                .createdAt(wr.getCreatedAt())
                .updatedAt(wr.getUpdatedAt())
                .build();
    }

    private static String toLabel(WithdrawalStatus status) {
        return switch (status) {
            case INITIATED, OTP_PENDING  -> "Đang xử lý";
            case FUNDS_LOCKED,
                 TRANSFER_INITIATED,
                 PROCESSING              -> "Đang chuyển tiền";
            case COMPLETED               -> "Thành công";
            case TRANSFER_FAILED,
                 FAILED                  -> "Thất bại";
            case CANCELLED               -> "Đã huỷ";
            case FUNDS_RELEASED          -> "Tiền đã hoàn về ví";
        };
    }
}
