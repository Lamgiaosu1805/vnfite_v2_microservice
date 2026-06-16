package com.p2plending.loan.dto.request;

import com.p2plending.loan.domain.enums.PaymentChannel;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class RecordPaymentRequest {

    @NotNull(message = "Số tiền trả là bắt buộc")
    @DecimalMin(value = "0.01", message = "Số tiền trả phải lớn hơn 0")
    private BigDecimal amount;

    /** Lý do ghi nhận thủ công — bắt buộc để có audit trail (vd: "TIKLUY downtime", "thu tiền mặt"). */
    @NotBlank(message = "Lý do ghi nhận là bắt buộc (reason)")
    private String reason;

    /** Thời điểm ghi nhận tiền về — null = thời điểm hiện tại. Mốc tính DPD. */
    private LocalDateTime paidAt;

    /** Kênh ghi nhận — null = MANUAL_ADMIN. */
    private PaymentChannel channel;

    private String externalRef;

    private String recordedBy;

    private String note;
}
