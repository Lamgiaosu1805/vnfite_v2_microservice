package com.p2plending.cms.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Admin ghi nhận một lần trả nợ thủ công khi khách trả tiền mặt / chuyển khoản
 * ngoài luồng ví VNFITE. Tiền được áp vào kỳ sớm nhất chưa trả ở loan-service.
 */
@Data
public class RecordRepaymentRequest {

    @NotNull(message = "Số tiền trả là bắt buộc")
    @DecimalMin(value = "0.01", message = "Số tiền trả phải lớn hơn 0")
    private BigDecimal amount;

    /** Bắt buộc — phục vụ audit trail (vd: "Khách chuyển khoản MB", "Thu tiền mặt tại quầy"). */
    @NotBlank(message = "Lý do ghi nhận là bắt buộc")
    @Size(max = 500)
    private String reason;

    /** Mã tham chiếu ngoài (mã giao dịch ngân hàng, biên lai...) — tuỳ chọn. */
    @Size(max = 255)
    private String externalRef;
}
